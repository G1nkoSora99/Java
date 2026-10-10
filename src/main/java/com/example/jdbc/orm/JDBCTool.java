package com.example.jdbc.orm;

import com.example.jdbc.orm.annotation.Column;
import com.example.jdbc.orm.annotation.Id;
import com.example.jdbc.orm.annotation.Table;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 通用工具类（简易 ORM 实现）。
 *
 * <p><b>核心思想（ORM = Object Relational Mapping，对象关系映射）：</b></p>
 * <ol>
 *   <li>用注解 {@link Table}/{@link Column}/{@link Id} 描述「Java 类 &lt;-&gt; 数据库表」的对应关系；</li>
 *   <li>用 <b>Java 反射</b> 在运行时读取这些映射关系、遍历属性、取值/赋值，从而自动拼装 SQL 并回填结果，
 *       做到「一个工具类服务所有实体」，不需要为每张表手写一遍 JDBC 样板代码；</li>
 *   <li>用 {@link PreparedStatement} 的 {@code ?} 占位符 + {@code setObject} 绑定参数，避免 SQL 注入。</li>
 * </ol>
 *
 * <p>本类对外提供 5 个统一的数据访问方法（作业要求的方法 1 ~ 方法 5）：</p>
 * <pre>
 * 方法1  {@link #resultSetToList(ResultSet, Class)}   结果集 -> 对象列表
 * 方法2  {@link #save(Object, Connection)}            新增
 * 方法3  {@link #update(Object, Connection)}          更新
 * 方法4  {@link #delete(Object, Connection)}          删除
 * 方法5  {@link #getOneById(String, Class, Connection)} 按主键查询单个对象
 * </pre>
 *
 * <p>以及一组辅助方法：获取连接、释放资源、读取映射元数据、类型转换、SQL 拼装、参数绑定、行映射等。</p>
 *
 * @param <T> 各方法自身的泛型参数，见具体方法签名
 * @author homework
 */
public final class JDBCTool {

    /** 默认的数据库配置文件（位于 src/main/resources 下）。 */
    private static final String DEFAULT_CONFIG_RESOURCE = "db.properties";

    /** 数据库配置。 */
    private static final Properties CONFIG = new Properties();

    /** 实体映射元数据缓存：Class -> EntityMeta，避免每次操作都重复反射解析。 */
    private static final Map<Class<?>, EntityMeta> META_CACHE = new ConcurrentHashMap<>();

    static {
        loadDefaultConfig();
    }

    /** 工具类禁止实例化。 */
    private JDBCTool() {
        throw new UnsupportedOperationException("JDBCTool 是工具类，不允许实例化");
    }

    // ==================================================================================
    // 一、辅助方法：配置加载与连接管理
    // ==================================================================================

    /** 加载默认配置文件 db.properties。 */
    private static void loadDefaultConfig() {
        loadConfig(DEFAULT_CONFIG_RESOURCE);
    }

    /**
     * 从类路径加载数据库配置（辅助方法）。
     *
     * @param resourceName 类路径下的配置文件名，例如 {@code db.properties}
     */
    public static synchronized void loadConfig(String resourceName) {
        try (InputStream in = JDBCTool.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                CONFIG.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new DataAccessException("读取数据库配置文件失败: " + resourceName, e);
        }
        // 兜底默认值，保证配置文件缺失时也能给出明确的连接目标
        CONFIG.putIfAbsent("jdbc.driver", "com.mysql.cj.jdbc.Driver");
        CONFIG.putIfAbsent("jdbc.url",
                "jdbc:mysql://localhost:3306/DateTest?useUnicode=true&characterEncoding=utf8"
                        + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        CONFIG.putIfAbsent("jdbc.username", "root");
        CONFIG.putIfAbsent("jdbc.password", "");
    }

    /** @return 当前使用的 JDBC URL。 */
    public static String getUrl() {
        return CONFIG.getProperty("jdbc.url");
    }

    /** @return 当前使用的数据库用户名。 */
    public static String getUsername() {
        return CONFIG.getProperty("jdbc.username");
    }

    /** @return 连接配置的简要描述（不含密码与连接参数），可用于日志输出。 */
    public static String describeConnection() {
        String url = getUrl() == null ? "" : getUrl().replaceFirst("\\?.*$", "");
        return url + "  [user=" + getUsername() + "]";
    }

    /**
     * 获取数据库连接（辅助方法）。
     *
     * <p>内部完成两件事：加载驱动类、通过 {@link DriverManager} 建立连接。
     * 调用方负责关闭连接（推荐 try-with-resources 或 {@link #close(AutoCloseable...)}）。</p>
     *
     * @return 数据库连接
     */
    public static Connection getConnection() {
        String driver = CONFIG.getProperty("jdbc.driver");
        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            return DriverManager.getConnection(getUrl(), getUsername(), CONFIG.getProperty("jdbc.password"));
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("找不到 JDBC 驱动类: " + driver + "（请确认 pom.xml 已引入 mysql-connector-j）", e);
        } catch (SQLException e) {
            throw new DataAccessException("获取数据库连接失败: " + getUrl(), e);
        }
    }

    /**
     * 安静地关闭任意数量的 JDBC 资源（辅助方法），自动忽略 null 与关闭异常。
     *
     * @param resources 需要关闭的资源，顺序建议 ResultSet -> Statement -> Connection
     */
    public static void close(AutoCloseable... resources) {
        if (resources == null) {
            return;
        }
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception ignored) {
                // 关闭失败不影响主流程
            }
        }
    }

    // ==================================================================================
    // 二、对外统一的 5 个数据访问方法
    // ==================================================================================

    /**
     * 【方法1】把任意 ResultSet 转换成目标类型的对象列表。
     *
     * <p>映射规则：结果集的列标签（或列名）与实体属性/配置的列名匹配（忽略大小写与下划线差异），
     * 匹配成功即通过反射为对象属性赋值；结果集中多余的列被忽略，
     * 因此本方法同样支持只查询部分列的自定义 SQL。</p>
     *
     * <p>注意：本方法只负责读取，不会关闭传入的 ResultSet，由调用方负责释放。</p>
     *
     * @param rs    已经执行查询得到的 ResultSet
     * @param clazz 目标实体类的 Class 对象
     * @param <T>   实体类型
     * @return 对象列表；没有任何记录时返回空列表（不是 null）
     */
    public static <T> List<T> resultSetToList(ResultSet rs, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (rs == null || clazz == null) {
            return list;
        }
        try {
            RowMapper<T> mapper = RowMapper.of(rs, clazz);
            while (rs.next()) {
                list.add(mapper.mapCurrentRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("ResultSet 转换为 " + clazz.getSimpleName() + " 列表失败", e);
        }
        return list;
    }

    /**
     * 【方法2】新增一条记录（对应 SQL 的 INSERT）。
     *
     * <p>由反射读取实体的所有非自增字段生成 INSERT 语句；
     * 若主键是自增列，执行后会把数据库生成的主键值通过反射回填到对象的 @Id 属性上。</p>
     *
     * @param obj        待保存的实体对象
     * @param connection 数据库连接
     * @param <T>        实体类型
     * @return 受影响的行数（成功为 1）
     */
    public static <T> int save(T obj, Connection connection) {
        requireEntity(obj, connection);
        EntityMeta meta = metaOf(obj.getClass());
        if (meta.insertColumns.isEmpty()) {
            throw new DataAccessException("实体 " + meta.clazz.getName() + " 没有可插入的字段");
        }
        String sql = buildInsertSql(meta);
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindFields(ps, meta.insertColumns, obj, 1);
            int rows = ps.executeUpdate();
            if (rows > 0 && meta.autoIncrement) {
                fillGeneratedKey(obj, meta, ps);
            }
            return rows;
        } catch (SQLException e) {
            throw new DataAccessException("新增失败: " + sql, e);
        }
    }

    /**
     * 【方法3】按主键更新一条记录（对应 SQL 的 UPDATE ... WHERE 主键 = ?）。
     *
     * <p>SET 部分包含实体中除主键外的全部字段，主键值取自实体的 @Id 属性。</p>
     *
     * @param obj        携带新值且主键不为空的实体对象
     * @param connection 数据库连接
     * @param <T>        实体类型
     * @return 受影响的行数（1 表示更新成功，0 表示主键不存在）
     */
    public static <T> int update(T obj, Connection connection) {
        requireEntity(obj, connection);
        EntityMeta meta = metaOf(obj.getClass());
        if (meta.updateColumns.isEmpty()) {
            throw new DataAccessException("实体 " + meta.clazz.getName() + " 没有可更新的普通字段");
        }
        Object idValue = getFieldValue(obj, meta.idField);
        if (idValue == null) {
            throw new DataAccessException("更新失败：主键 " + meta.idColumn + " 为空，无法定位记录");
        }
        String sql = buildUpdateSql(meta);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int nextIndex = bindFields(ps, meta.updateColumns, obj, 1);
            ps.setObject(nextIndex, idValue);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("更新失败: " + sql, e);
        }
    }

    /**
     * 【方法4】按主键删除一条记录（对应 SQL 的 DELETE FROM ... WHERE 主键 = ?）。
     *
     * @param obj        只需主键有值的实体对象
     * @param connection 数据库连接
     * @param <T>        实体类型
     * @return 受影响的行数（1 表示删除成功，0 表示主键不存在）
     */
    public static <T> int delete(T obj, Connection connection) {
        requireEntity(obj, connection);
        EntityMeta meta = metaOf(obj.getClass());
        Object idValue = getFieldValue(obj, meta.idField);
        if (idValue == null) {
            throw new DataAccessException("删除失败：主键 " + meta.idColumn + " 为空，无法定位记录");
        }
        String sql = buildDeleteSql(meta);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, idValue);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("删除失败: " + sql, e);
        }
    }

    /**
     * 【方法5】按主键查询单个对象（对应 SQL 的 SELECT * FROM ... WHERE 主键 = ?）。
     *
     * @param id         主键值（字符串形式，内部会按主键属性类型自动转换）
     * @param clazz      目标实体类的 Class 对象
     * @param connection 数据库连接
     * @param <T>        实体类型
     * @return 查询到的对象；不存在时返回 {@code null}
     */
    public static <T> T getOneById(String id, Class<T> clazz, Connection connection) {
        if (clazz == null) {
            throw new DataAccessException("查询失败：clazz 不能为 null");
        }
        if (connection == null) {
            throw new DataAccessException("查询失败：connection 不能为 null");
        }
        EntityMeta meta = metaOf(clazz);
        Object idValue = convertValue(meta.idField.getType(), id);
        if (idValue == null) {
            return null;
        }
        String sql = buildSelectByIdSql(meta);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, idValue);
            try (ResultSet rs = ps.executeQuery()) {
                RowMapper<T> mapper = RowMapper.of(rs, clazz);
                return rs.next() ? mapper.mapCurrentRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("按主键查询失败: " + sql, e);
        }
    }

    // ==================================================================================
    // 三、辅助方法：基于「方法1」的通用查询与通用更新
    // ==================================================================================

    /**
     * 通用查询（辅助方法）：执行任意 SELECT 并把结果映射成对象列表，内部复用方法1。
     *
     * @param sql        带 {@code ?} 占位符的查询语句
     * @param clazz      目标实体类
     * @param connection 数据库连接
     * @param params     占位符参数，按顺序绑定
     * @param <T>        实体类型
     * @return 对象列表
     */
    public static <T> List<T> query(String sql, Class<T> clazz, Connection connection, Object... params) {
        if (connection == null) {
            throw new DataAccessException("查询失败：connection 不能为 null");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return resultSetToList(rs, clazz);
            }
        } catch (SQLException e) {
            throw new DataAccessException("查询失败: " + sql, e);
        }
    }

    /**
     * 通用增删改（辅助方法）：执行任意 DML/DDL 语句。
     *
     * @param sql        带 {@code ?} 占位符的 SQL 语句
     * @param connection 数据库连接
     * @param params     占位符参数，按顺序绑定
     * @return 受影响的行数
     */
    public static int executeUpdate(String sql, Connection connection, Object... params) {
        if (connection == null) {
            throw new DataAccessException("执行失败：connection 不能为 null");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("执行失败: " + sql, e);
        }
    }

    /**
     * 统计符合条件的记录数（辅助方法），用于演示与校验。
     *
     * @param tableName  表名
     * @param connection 数据库连接
     * @return 记录数
     */
    public static long count(String tableName, Connection connection) {
        String sql = "SELECT COUNT(*) FROM `" + tableName + "`";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("统计失败: " + sql, e);
        }
    }

    // ==================================================================================
    // 四、辅助方法：映射元数据（反射解析 类/属性 <-> 表/列）
    // ==================================================================================

    /**
     * 获取（并缓存）实体的映射元数据（辅助方法）。
     *
     * @param clazz 实体类
     * @return 元数据
     */
    private static EntityMeta metaOf(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, JDBCTool::buildMeta);
    }

    /** @return 实体对应的表名。 */
    public static String tableNameOf(Class<?> clazz) {
        return metaOf(clazz).tableName;
    }

    /** @return 实体的主键列名。 */
    public static String idColumnOf(Class<?> clazz) {
        return metaOf(clazz).idColumn;
    }

    /**
     * 生成「属性 &lt;-&gt; 列名」的映射说明（辅助方法），用于演示与排查问题。
     *
     * @param clazz 实体类
     * @return 形如 {@code id -> id, enrollmentDate -> enrollment_date} 的文本
     */
    public static String describeMapping(Class<?> clazz) {
        EntityMeta meta = metaOf(clazz);
        StringBuilder sb = new StringBuilder();
        for (FieldColumn fc : meta.allColumns) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(fc.field.getName()).append(" -> ").append(fc.column);
            if (fc.field.equals(meta.idField)) {
                sb.append(meta.autoIncrement ? " [主键/自增]" : " [主键]");
            }
        }
        return "表 " + meta.tableName + " : " + sb;
    }

    /**
     * 预览由反射自动生成的四种 SQL 语句（辅助方法），用于教学演示与排查问题。
     *
     * @param clazz 实体类
     * @return 多行文本形式的 SQL 预览
     */
    public static String describeSql(Class<?> clazz) {
        EntityMeta meta = metaOf(clazz);
        String nl = System.lineSeparator();
        return "  INSERT  : " + buildInsertSql(meta) + nl
                + "  SELECT  : " + buildSelectByIdSql(meta) + nl
                + "  UPDATE  : " + buildUpdateSql(meta) + nl
                + "  DELETE  : " + buildDeleteSql(meta);
    }

    /** 反射解析一个实体类，构建映射元数据。 */
    private static EntityMeta buildMeta(Class<?> clazz) {
        if (clazz == null) {
            throw new DataAccessException("实体 Class 不能为 null");
        }
        if (clazz.isInterface() || clazz.isPrimitive() || clazz.isArray() || clazz.isEnum()
                || clazz.isAnnotation()) {
            throw new DataAccessException("不支持的实体类型: " + clazz.getName());
        }

        Table table = clazz.getAnnotation(Table.class);
        String tableName = (table != null && !table.value().isBlank())
                ? table.value().trim()
                : toSnakeCase(clazz.getSimpleName());

        List<FieldColumn> allColumns = new ArrayList<>();
        Field idField = null;
        String idColumn = null;
        boolean autoIncrement = false;

        for (Class<?> current = clazz; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                Id id = field.getAnnotation(Id.class);
                field.setAccessible(true); // 反射访问私有属性
                String columnName = (column != null && !column.value().isBlank())
                        ? column.value().trim()
                        : toSnakeCase(field.getName());
                allColumns.add(new FieldColumn(field, columnName));
                if (id != null) {
                    if (idField != null) {
                        throw new DataAccessException(clazz.getName() + " 只能有一个 @Id 主键字段");
                    }
                    idField = field;
                    idColumn = columnName;
                    autoIncrement = id.autoIncrement();
                }
            }
        }

        if (allColumns.isEmpty()) {
            throw new DataAccessException(clazz.getName() + " 没有任何可映射的属性");
        }
        if (idField == null) {
            throw new DataAccessException(clazz.getName() + " 缺少 @Id 标注的主键属性");
        }

        List<FieldColumn> insertColumns = new ArrayList<>();
        List<FieldColumn> updateColumns = new ArrayList<>();
        for (FieldColumn fc : allColumns) {
            if (fc.field.equals(idField)) {
                if (!autoIncrement) {
                    insertColumns.add(fc); // 非自增主键需要手工写入
                }
                continue; // 主键永远不参与 SET
            }
            insertColumns.add(fc);
            updateColumns.add(fc);
        }
        return new EntityMeta(clazz, tableName, idField, idColumn, autoIncrement,
                List.copyOf(allColumns), List.copyOf(insertColumns), List.copyOf(updateColumns));
    }

    // ==================================================================================
    // 五、辅助方法：SQL 拼装与参数绑定
    // ==================================================================================

    /** 拼装 INSERT 语句（辅助方法）。 */
    private static String buildInsertSql(EntityMeta meta) {
        StringBuilder columns = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        for (FieldColumn fc : meta.insertColumns) {
            if (columns.length() > 0) {
                columns.append(", ");
                marks.append(", ");
            }
            columns.append('`').append(fc.column).append('`');
            marks.append('?');
        }
        return "INSERT INTO `" + meta.tableName + "` (" + columns + ") VALUES (" + marks + ")";
    }

    /** 拼装 UPDATE 语句（辅助方法）。 */
    private static String buildUpdateSql(EntityMeta meta) {
        StringBuilder sets = new StringBuilder();
        for (FieldColumn fc : meta.updateColumns) {
            if (sets.length() > 0) {
                sets.append(", ");
            }
            sets.append('`').append(fc.column).append("` = ?");
        }
        return "UPDATE `" + meta.tableName + "` SET " + sets
                + " WHERE `" + meta.idColumn + "` = ?";
    }

    /** 拼装 DELETE 语句（辅助方法）。 */
    private static String buildDeleteSql(EntityMeta meta) {
        return "DELETE FROM `" + meta.tableName + "` WHERE `" + meta.idColumn + "` = ?";
    }

    /** 拼装按主键查询的 SELECT 语句（辅助方法）。 */
    private static String buildSelectByIdSql(EntityMeta meta) {
        return "SELECT * FROM `" + meta.tableName + "` WHERE `" + meta.idColumn + "` = ?";
    }

    /** 把实体属性值依次绑定到 PreparedStatement 的占位符上（辅助方法）。 */
    private static int bindFields(PreparedStatement ps, List<FieldColumn> columns, Object target,
                                 int startIndex) throws SQLException {
        int index = startIndex;
        for (FieldColumn fc : columns) {
            ps.setObject(index++, getFieldValue(target, fc.field));
        }
        return index;
    }

    /** 绑定任意参数数组到 PreparedStatement（辅助方法）。 */
    private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** 读取数据库生成的自增主键，并反射回填到实体对象（辅助方法）。 */
    private static void fillGeneratedKey(Object obj, EntityMeta meta, PreparedStatement ps)
            throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                Object generated = convertValue(meta.idField.getType(), keys.getObject(1));
                if (generated != null) {
                    setFieldValue(obj, meta.idField, generated);
                }
            }
        }
    }

    // ==================================================================================
    // 六、辅助方法：反射取值/赋值、类型转换、命名转换
    // ==================================================================================

    /** 通过反射创建实体实例（辅助方法）。 */
    public static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new DataAccessException(clazz.getName()
                    + " 缺少无参构造方法，无法通过反射创建对象（ORM 需要无参构造 + setter）", e);
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException("反射创建对象失败: " + clazz.getName(), e);
        }
    }

    /** 反射读取属性值（辅助方法），自动处理私有属性。 */
    public static Object getFieldValue(Object obj, Field field) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new DataAccessException("反射读取属性失败: " + obj.getClass().getName()
                    + "." + field.getName(), e);
        }
    }

    /** 反射写入属性值（辅助方法），自动处理私有属性。 */
    public static void setFieldValue(Object obj, Field field, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("反射赋值类型不匹配: " + obj.getClass().getName()
                    + "." + field.getName() + " 期望 " + field.getType().getSimpleName()
                    + "，实际 " + (value == null ? "null" : value.getClass().getSimpleName()), e);
        } catch (IllegalAccessException e) {
            throw new DataAccessException("反射写入属性失败: " + obj.getClass().getName()
                    + "." + field.getName(), e);
        }
    }

    /**
     * 类型转换（辅助方法）：把数据库返回的值（或字符串主键）转换成实体属性的目标类型。
     *
     * <p>支持基本类型包装类、String、BigDecimal/BigInteger、Boolean、Character、枚举、
     * java.util.Date、LocalDate/LocalDateTime/LocalTime 等常见类型。</p>
     *
     * @param targetType 目标类型（实体属性的类型）
     * @param value      原始值，可以为 null
     * @return 转换后的值；无法识别时原样返回
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convertValue(Class<?> targetType, Object value) {
        if (targetType == null) {
            return value;
        }
        if (value == null) {
            return primitiveDefault(targetType);
        }
        Class<?> type = wrap(targetType);
        if (type == Object.class) {
            return value;
        }
        if (type.isInstance(value)) {
            return value;
        }
        if (type == String.class) {
            return value.toString();
        }
        if (type == Integer.class) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1 : 0;
            }
            return Integer.valueOf(value.toString().trim());
        }
        if (type == Long.class) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.valueOf(value.toString().trim());
        }
        if (type == Short.class) {
            if (value instanceof Number number) {
                return number.shortValue();
            }
            return Short.valueOf(value.toString().trim());
        }
        if (type == Byte.class) {
            if (value instanceof Number number) {
                return number.byteValue();
            }
            return Byte.valueOf(value.toString().trim());
        }
        if (type == Double.class) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.valueOf(value.toString().trim());
        }
        if (type == Float.class) {
            if (value instanceof Number number) {
                return number.floatValue();
            }
            return Float.valueOf(value.toString().trim());
        }
        if (type == BigDecimal.class) {
            return (value instanceof Number) ? new BigDecimal(value.toString())
                    : new BigDecimal(value.toString().trim());
        }
        if (type == BigInteger.class) {
            return new BigInteger(value.toString().trim());
        }
        if (type == Boolean.class) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            String text = value.toString().trim();
            return "1".equals(text) || "true".equalsIgnoreCase(text)
                    || "yes".equalsIgnoreCase(text) || "y".equalsIgnoreCase(text);
        }
        if (type == Character.class) {
            String text = value.toString();
            return text.isEmpty() ? null : text.charAt(0);
        }
        if (type == LocalDate.class) {
            if (value instanceof java.sql.Date date) {
                return date.toLocalDate();
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime().toLocalDate();
            }
            if (value instanceof java.util.Date date) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            return LocalDate.parse(value.toString().trim());
        }
        if (type == LocalDateTime.class) {
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
            if (value instanceof java.sql.Date date) {
                return date.toLocalDate().atStartOfDay();
            }
            if (value instanceof java.util.Date date) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            String text = value.toString().trim();
            return text.length() <= 10
                    ? LocalDate.parse(text).atStartOfDay()
                    : LocalDateTime.parse(text.replace(' ', 'T'));
        }
        if (type == LocalTime.class) {
            if (value instanceof java.sql.Time time) {
                return time.toLocalTime();
            }
            return LocalTime.parse(value.toString().trim());
        }
        if (type == java.util.Date.class) {
            if (value instanceof java.util.Date date) {
                return date;
            }
            if (value instanceof LocalDateTime dateTime) {
                return java.util.Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
            }
            if (value instanceof LocalDate date) {
                return java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
            return value;
        }
        if (type.isEnum()) {
            String text = value.toString().trim();
            for (Object constant : type.getEnumConstants()) {
                if (((Enum) constant).name().equalsIgnoreCase(text)) {
                    return constant;
                }
            }
            return Enum.valueOf((Class<Enum>) type, text);
        }
        return value;
    }

    /** 基本类型的默认值（辅助方法），用于给 null 值兜底。 */
    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    /** 基本类型 -> 包装类型（辅助方法）。 */
    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    /**
     * 驼峰命名转下划线小写（辅助方法）：{@code enrollmentDate -> enrollment_date}。
     *
     * <p>这是「约定优于配置」的一部分：属性不写 @Column 时，按此规则推导列名。</p>
     */
    public static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean prevLowerCase = i > 0 && Character.isLowerCase(name.charAt(i - 1));
                boolean nextLowerCase = i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
                if (i > 0 && (prevLowerCase || nextLowerCase)) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 归一化列名（辅助方法）：去空格、去反引号、转小写，用于宽松匹配。 */
    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replace("`", "").toLowerCase(Locale.ROOT);
    }

    /** 校验实体与连接（辅助方法）。 */
    private static void requireEntity(Object obj, Connection connection) {
        if (obj == null) {
            throw new DataAccessException("实体对象不能为 null");
        }
        if (connection == null) {
            throw new DataAccessException("数据库连接不能为 null");
        }
    }

    // ==================================================================================
    // 七、内部数据结构：映射元数据 / 行映射器 / 属性-列对
    // ==================================================================================

    /** 实体映射元数据：由反射解析一次后缓存复用。 */
    private static final class EntityMeta {

        /** 实体类。 */
        final Class<?> clazz;
        /** 表名。 */
        final String tableName;
        /** 主键属性。 */
        final Field idField;
        /** 主键列名。 */
        final String idColumn;
        /** 主键是否自增。 */
        final boolean autoIncrement;
        /** 全部参与映射的属性。 */
        final List<FieldColumn> allColumns;
        /** INSERT 使用的属性。 */
        final List<FieldColumn> insertColumns;
        /** UPDATE 的 SET 部分使用的属性。 */
        final List<FieldColumn> updateColumns;

        EntityMeta(Class<?> clazz, String tableName, Field idField, String idColumn,
                   boolean autoIncrement, List<FieldColumn> allColumns,
                   List<FieldColumn> insertColumns, List<FieldColumn> updateColumns) {
            this.clazz = clazz;
            this.tableName = tableName;
            this.idField = idField;
            this.idColumn = idColumn;
            this.autoIncrement = autoIncrement;
            this.allColumns = allColumns;
            this.insertColumns = insertColumns;
            this.updateColumns = updateColumns;
        }

        /** 构建「归一化列名 -> 属性」的索引，用于结果集列与属性的宽松匹配。 */
        Map<String, Field> columnIndex() {
            Map<String, Field> index = new LinkedHashMap<>();
            for (FieldColumn fc : allColumns) {
                String column = normalize(fc.column);
                String property = normalize(fc.field.getName());
                index.putIfAbsent(column, fc.field);
                index.putIfAbsent(property, fc.field);
                index.putIfAbsent(column.replace("_", ""), fc.field);
                index.putIfAbsent(property.replace("_", ""), fc.field);
            }
            return index;
        }
    }

    /** 属性与列名的对应关系。 */
    private static final class FieldColumn {

        /** 实体属性。 */
        final Field field;
        /** 数据库列名。 */
        final String column;

        FieldColumn(Field field, String column) {
            this.field = field;
            this.column = column;
        }
    }

    /**
     * 行映射器：预先解析结果集的列结构，逐行通过反射创建对象并赋值。
     * 这正是「方法1」把结果集变成对象列表的关键实现。
     */
    private static final class RowMapper<T> {

        private final Class<T> clazz;
        private final int columnCount;
        /** 与结果集列下标一一对应的实体属性；null 表示该列不需要映射。 */
        private final Field[] fields;

        private RowMapper(Class<T> clazz, int columnCount, Field[] fields) {
            this.clazz = clazz;
            this.columnCount = columnCount;
            this.fields = fields;
        }

        /** 解析结果集元数据，建立「列 -> 属性」的映射。 */
        static <T> RowMapper<T> of(ResultSet rs, Class<T> clazz) throws SQLException {
            EntityMeta meta = metaOf(clazz);
            Map<String, Field> index = meta.columnIndex();
            ResultSetMetaData md = rs.getMetaData();
            int count = md.getColumnCount();
            Field[] fields = new Field[count + 1];
            for (int i = 1; i <= count; i++) {
                String label = normalize(md.getColumnLabel(i));
                Field field = index.get(label);
                if (field == null) {
                    field = index.get(label.replace("_", ""));
                }
                fields[i] = field;
            }
            return new RowMapper<>(clazz, count, fields);
        }

        /** 把结果集当前行转换为实体对象。 */
        T mapCurrentRow(ResultSet rs) throws SQLException {
            T instance = newInstance(clazz);
            for (int i = 1; i <= columnCount; i++) {
                Field field = fields[i];
                if (field == null) {
                    continue; // 结果集中多余的列，忽略
                }
                Object value = convertValue(field.getType(), rs.getObject(i));
                if (value == null && field.getType().isPrimitive()) {
                    continue;
                }
                setFieldValue(instance, field, value);
            }
            return instance;
        }
    }
}
