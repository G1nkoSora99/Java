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

public final class JDBCTool {

    private static final String DEFAULT_CONFIG_RESOURCE = "db.properties";

    private static final Properties CONFIG = new Properties();

    private static final Map<Class<?>, EntityMeta> META_CACHE = new ConcurrentHashMap<>();

    static {
        loadDefaultConfig();
    }

    private JDBCTool() {
        throw new UnsupportedOperationException("JDBCTool is a utility class");
    }

    private static void loadDefaultConfig() {
        loadConfig(DEFAULT_CONFIG_RESOURCE);
    }

    public static synchronized void loadConfig(String resourceName) {
        try (InputStream in = JDBCTool.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                CONFIG.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new DataAccessException("config load failed: " + resourceName, e);
        }
        CONFIG.putIfAbsent("jdbc.driver", "com.mysql.cj.jdbc.Driver");
        CONFIG.putIfAbsent("jdbc.url",
                "jdbc:mysql://localhost:3306/DateTest?useUnicode=true&characterEncoding=utf8"
                        + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        CONFIG.putIfAbsent("jdbc.username", "root");
        CONFIG.putIfAbsent("jdbc.password", "");
    }

    public static String getUrl() {
        return CONFIG.getProperty("jdbc.url");
    }

    public static String getUsername() {
        return CONFIG.getProperty("jdbc.username");
    }

    public static Connection getConnection() {
        String driver = CONFIG.getProperty("jdbc.driver");
        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            return DriverManager.getConnection(getUrl(), getUsername(), CONFIG.getProperty("jdbc.password"));
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("driver not found: " + driver, e);
        } catch (SQLException e) {
            throw new DataAccessException("connect failed: " + getUrl() + " -> " + e.getMessage(), e);
        }
    }

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
            }
        }
    }

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
            throw new DataAccessException("resultSetToList failed: " + clazz.getName() + " -> " + e.getMessage(), e);
        }
        return list;
    }

    public static <T> int save(T obj, Connection connection) {
        requireEntity(obj, connection);
        EntityMeta meta = metaOf(obj.getClass());
        if (meta.insertColumns.isEmpty()) {
            throw new DataAccessException("no insertable column: " + meta.clazz.getName());
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
            throw new DataAccessException("save failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static <T> int update(T obj, Connection connection) {
        requireEntity(obj, connection);
        EntityMeta meta = metaOf(obj.getClass());
        if (meta.updateColumns.isEmpty()) {
            throw new DataAccessException("no updatable column: " + meta.clazz.getName());
        }
        Object idValue = getFieldValue(obj, meta.idField);
        if (idValue == null) {
            throw new DataAccessException("update failed: " + meta.idColumn + " is null");
        }
        String sql = buildUpdateSql(meta);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int nextIndex = bindFields(ps, meta.updateColumns, obj, 1);
            ps.setObject(nextIndex, idValue);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static <T> int delete(T obj, Connection connection) {
        requireEntity(obj, connection);
        EntityMeta meta = metaOf(obj.getClass());
        Object idValue = getFieldValue(obj, meta.idField);
        if (idValue == null) {
            throw new DataAccessException("delete failed: " + meta.idColumn + " is null");
        }
        String sql = buildDeleteSql(meta);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, idValue);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("delete failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static <T> T getOneById(String id, Class<T> clazz, Connection connection) {
        if (clazz == null) {
            throw new DataAccessException("clazz must not be null");
        }
        if (connection == null) {
            throw new DataAccessException("connection must not be null");
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
            throw new DataAccessException("getOneById failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static <T> List<T> query(String sql, Class<T> clazz, Connection connection, Object... params) {
        if (connection == null) {
            throw new DataAccessException("connection must not be null");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return resultSetToList(rs, clazz);
            }
        } catch (SQLException e) {
            throw new DataAccessException("query failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static int executeUpdate(String sql, Connection connection, Object... params) {
        if (connection == null) {
            throw new DataAccessException("connection must not be null");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("executeUpdate failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static long count(String tableName, Connection connection) {
        String sql = "SELECT COUNT(*) FROM `" + tableName + "`";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("count failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    private static EntityMeta metaOf(Class<?> clazz) {
        return META_CACHE.computeIfAbsent(clazz, JDBCTool::buildMeta);
    }

    public static String tableNameOf(Class<?> clazz) {
        return metaOf(clazz).tableName;
    }

    public static String idColumnOf(Class<?> clazz) {
        return metaOf(clazz).idColumn;
    }

    private static EntityMeta buildMeta(Class<?> clazz) {
        if (clazz == null) {
            throw new DataAccessException("entity class must not be null");
        }
        if (clazz.isInterface() || clazz.isPrimitive() || clazz.isArray() || clazz.isEnum()
                || clazz.isAnnotation()) {
            throw new DataAccessException("unsupported entity type: " + clazz.getName());
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
                field.setAccessible(true);
                String columnName = (column != null && !column.value().isBlank())
                        ? column.value().trim()
                        : toSnakeCase(field.getName());
                allColumns.add(new FieldColumn(field, columnName));
                if (id != null) {
                    if (idField != null) {
                        throw new DataAccessException("duplicate @Id: " + clazz.getName());
                    }
                    idField = field;
                    idColumn = columnName;
                    autoIncrement = id.autoIncrement();
                }
            }
        }

        if (allColumns.isEmpty()) {
            throw new DataAccessException("no mapped field: " + clazz.getName());
        }
        if (idField == null) {
            throw new DataAccessException("missing @Id: " + clazz.getName());
        }

        List<FieldColumn> insertColumns = new ArrayList<>();
        List<FieldColumn> updateColumns = new ArrayList<>();
        for (FieldColumn fc : allColumns) {
            if (fc.field.equals(idField)) {
                if (!autoIncrement) {
                    insertColumns.add(fc);
                }
                continue;
            }
            insertColumns.add(fc);
            updateColumns.add(fc);
        }
        return new EntityMeta(clazz, tableName, idField, idColumn, autoIncrement,
                List.copyOf(allColumns), List.copyOf(insertColumns), List.copyOf(updateColumns));
    }

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

    private static String buildDeleteSql(EntityMeta meta) {
        return "DELETE FROM `" + meta.tableName + "` WHERE `" + meta.idColumn + "` = ?";
    }

    private static String buildSelectByIdSql(EntityMeta meta) {
        return "SELECT * FROM `" + meta.tableName + "` WHERE `" + meta.idColumn + "` = ?";
    }

    private static int bindFields(PreparedStatement ps, List<FieldColumn> columns, Object target,
                                 int startIndex) throws SQLException {
        int index = startIndex;
        for (FieldColumn fc : columns) {
            ps.setObject(index++, getFieldValue(target, fc.field));
        }
        return index;
    }

    private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

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

    public static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new DataAccessException("no default constructor: " + clazz.getName(), e);
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException("newInstance failed: " + clazz.getName(), e);
        }
    }

    public static Object getFieldValue(Object obj, Field field) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new DataAccessException("getFieldValue failed: " + field.getName(), e);
        }
    }

    public static void setFieldValue(Object obj, Field field, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("setFieldValue type mismatch: " + field.getName()
                    + " expects " + field.getType().getSimpleName()
                    + " but got " + (value == null ? "null" : value.getClass().getSimpleName()), e);
        } catch (IllegalAccessException e) {
            throw new DataAccessException("setFieldValue failed: " + field.getName(), e);
        }
    }

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
            return new BigDecimal(value.toString().trim());
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

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replace("`", "").toLowerCase(Locale.ROOT);
    }

    private static void requireEntity(Object obj, Connection connection) {
        if (obj == null) {
            throw new DataAccessException("entity must not be null");
        }
        if (connection == null) {
            throw new DataAccessException("connection must not be null");
        }
    }

    private static final class EntityMeta {

        final Class<?> clazz;
        final String tableName;
        final Field idField;
        final String idColumn;
        final boolean autoIncrement;
        final List<FieldColumn> allColumns;
        final List<FieldColumn> insertColumns;
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

    private static final class FieldColumn {

        final Field field;
        final String column;

        FieldColumn(Field field, String column) {
            this.field = field;
            this.column = column;
        }
    }

    private static final class RowMapper<T> {

        private final Class<T> clazz;
        private final int columnCount;
        private final Field[] fields;

        private RowMapper(Class<T> clazz, int columnCount, Field[] fields) {
            this.clazz = clazz;
            this.columnCount = columnCount;
            this.fields = fields;
        }

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

        T mapCurrentRow(ResultSet rs) throws SQLException {
            T instance = newInstance(clazz);
            for (int i = 1; i <= columnCount; i++) {
                Field field = fields[i];
                if (field == null) {
                    continue;
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
