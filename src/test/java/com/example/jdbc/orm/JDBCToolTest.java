package com.example.jdbc.orm;

import com.example.jdbc.entity.College;
import com.example.jdbc.entity.Student;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDBCTool 五个核心方法的单元测试。
 *
 * <p>测试依赖本地 MySQL 中的 DateTest 数据库；若数据库不可用，
 * 测试会通过 {@link Assumptions#abort} 自动跳过，而不是让构建失败。</p>
 *
 * @author homework
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("JDBCTool 增删改查功能测试")
class JDBCToolTest {

    private static Connection connection;
    private static Integer savedCollegeId;

    @BeforeAll
    static void setUp() {
        try {
            connection = JDBCTool.getConnection();
        } catch (RuntimeException e) {
            Assumptions.abort("跳过 JDBCToolTest：无法连接数据库 DateTest（" + e.getMessage() + "）");
        }
        JDBCTool.executeUpdate("DELETE FROM `student`", connection);
        JDBCTool.executeUpdate("DELETE FROM `college`", connection);
    }

    @AfterAll
    static void tearDown() {
        JDBCTool.close(connection);
    }

    @Test
    @Order(1)
    @DisplayName("方法2 save()：新增记录并通过反射回填自增主键")
    void testSave() {
        College college = new College("计算机学院", "CS");
        assertNull(college.getId(), "保存前主键应为 null");

        int rows = JDBCTool.save(college, connection);

        assertEquals(1, rows, "应新增 1 行");
        assertNotNull(college.getId(), "保存后应回填数据库生成的 id");
        savedCollegeId = college.getId();
    }

    @Test
    @Order(2)
    @DisplayName("方法5 getOneById()：按主键查询单个对象，字符串主键自动转型")
    void testGetOneById() {
        assertNotNull(savedCollegeId, "前置用例应已保存学院");
        College college = JDBCTool.getOneById(String.valueOf(savedCollegeId), College.class, connection);

        assertNotNull(college, "应能按主键查到刚保存的学院");
        assertEquals("计算机学院", college.getName());
        assertEquals("CS", college.getCode());
        assertEquals(savedCollegeId, college.getId());

        assertNull(JDBCTool.getOneById("999999", College.class, connection),
                "主键不存在时应返回 null");
    }

    @Test
    @Order(3)
    @DisplayName("方法3 update()：按主键更新实体")
    void testUpdate() {
        College college = new College("外国语学院", "FL");
        JDBCTool.save(college, connection);
        Integer id = college.getId();

        college.setName("外国语学院(国际学院)");
        college.setCode("FL-INTL");
        int rows = JDBCTool.update(college, connection);

        assertEquals(1, rows, "应更新 1 行");
        College reloaded = JDBCTool.getOneById(String.valueOf(id), College.class, connection);
        assertNotNull(reloaded);
        assertEquals("外国语学院(国际学院)", reloaded.getName());
        assertEquals("FL-INTL", reloaded.getCode());
    }

    @Test
    @Order(4)
    @DisplayName("方法1 resultSetToList()：ResultSet -> 对象列表，含日期/布尔/金额类型转换")
    void testResultSetToList() throws SQLException {
        JDBCTool.save(new Student("赵六", "数据科学与大数据技术", 19,
                LocalDate.of(2023, 9, 1), false, new BigDecimal("6200.00")), connection);

        String sql = "SELECT id, name, major, age, enrollment_date, graduated, tuition "
                + "FROM student ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Student> students = JDBCTool.resultSetToList(rs, Student.class);

            assertFalse(students.isEmpty(), "应至少映射出一个学生对象");
            Student student = students.get(0);
            assertNotNull(student.getId());
            assertEquals("赵六", student.getName());
            assertEquals("数据科学与大数据技术", student.getMajor());
            assertEquals(19, student.getAge());
            assertEquals(LocalDate.of(2023, 9, 1), student.getEnrollmentDate(),
                    "DATE 列应映射为 LocalDate");
            assertEquals(Boolean.FALSE, student.getGraduated(),
                    "TINYINT(1) 列应映射为 Boolean");
            assertEquals(0, new BigDecimal("6200.00").compareTo(student.getTuition()),
                    "DECIMAL 列应映射为 BigDecimal");
        }
    }

    @Test
    @Order(5)
    @DisplayName("方法1 resultSetToList()：结果集列少于属性时保持宽松映射")
    void testResultSetToListWithPartialColumns() {
        List<Student> students = JDBCTool.query(
                "SELECT id, name FROM student ORDER BY id", Student.class, connection);

        assertFalse(students.isEmpty());
        Student student = students.get(0);
        assertNotNull(student.getName(), "name 列应被映射");
        assertNull(student.getMajor(), "未查询的 major 列对应属性应为 null");
        assertNull(student.getTuition(), "未查询的 tuition 列对应属性应为 null");
    }

    @Test
    @Order(6)
    @DisplayName("方法4 delete()：按主键删除记录")
    void testDelete() {
        College college = new College("数学学院", "MATH");
        JDBCTool.save(college, connection);
        Integer id = college.getId();
        assertNotNull(JDBCTool.getOneById(String.valueOf(id), College.class, connection));

        int rows = JDBCTool.delete(college, connection);

        assertEquals(1, rows, "应删除 1 行");
        assertNull(JDBCTool.getOneById(String.valueOf(id), College.class, connection),
                "删除后应查不到该记录");
    }

    @Test
    @Order(7)
    @DisplayName("辅助方法：命名转换与类型转换")
    void testHelpers() {
        assertEquals("enrollment_date", JDBCTool.toSnakeCase("enrollmentDate"));
        assertEquals("id", JDBCTool.toSnakeCase("id"));
        assertEquals("student", JDBCTool.tableNameOf(Student.class));
        assertEquals("college", JDBCTool.tableNameOf(College.class));
        assertEquals("id", JDBCTool.idColumnOf(Student.class));

        assertEquals(18, JDBCTool.convertValue(Integer.class, "18"));
        assertEquals(18, JDBCTool.convertValue(Integer.class, 18L));
        assertEquals(Boolean.TRUE, JDBCTool.convertValue(Boolean.class, 1));
        assertEquals(Boolean.FALSE, JDBCTool.convertValue(Boolean.class, 0));
        assertEquals(LocalDate.of(2021, 9, 1),
                JDBCTool.convertValue(LocalDate.class, java.sql.Date.valueOf("2021-09-01")));
        assertTrue(JDBCTool.convertValue(String.class, 123).equals("123"));
    }
}
