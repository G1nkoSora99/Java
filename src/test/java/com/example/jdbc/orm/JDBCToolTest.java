package com.example.jdbc.orm;

import com.example.jdbc.entity.College;
import com.example.jdbc.entity.Student;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JDBCToolTest {

    private static Connection connection;
    private static Integer savedCollegeId;

    @BeforeAll
    static void setUp() {
        try {
            connection = JDBCTool.getConnection();
        } catch (RuntimeException e) {
            Assumptions.abort("database unavailable: " + e.getMessage());
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
    void testSave() {
        College college = new College("计算机学院", "CS");
        assertNull(college.getId());

        int rows = JDBCTool.save(college, connection);

        assertEquals(1, rows);
        assertNotNull(college.getId());
        savedCollegeId = college.getId();
    }

    @Test
    @Order(2)
    void testGetOneById() {
        assertNotNull(savedCollegeId);
        College college = JDBCTool.getOneById(String.valueOf(savedCollegeId), College.class, connection);

        assertNotNull(college);
        assertEquals("计算机学院", college.getName());
        assertEquals("CS", college.getCode());
        assertEquals(savedCollegeId, college.getId());

        assertNull(JDBCTool.getOneById("999999", College.class, connection));
    }

    @Test
    @Order(3)
    void testUpdate() {
        College college = new College("外国语学院", "FL");
        JDBCTool.save(college, connection);
        Integer id = college.getId();

        college.setName("外国语学院(国际学院)");
        college.setCode("FL-INTL");
        int rows = JDBCTool.update(college, connection);

        assertEquals(1, rows);
        College reloaded = JDBCTool.getOneById(String.valueOf(id), College.class, connection);
        assertNotNull(reloaded);
        assertEquals("外国语学院(国际学院)", reloaded.getName());
        assertEquals("FL-INTL", reloaded.getCode());
    }

    @Test
    @Order(4)
    void testResultSetToList() throws SQLException {
        JDBCTool.save(new Student("赵六", "数据科学与大数据技术", 19,
                LocalDate.of(2023, 9, 1), false, new BigDecimal("6200.00")), connection);

        String sql = "SELECT id, name, major, age, enrollment_date, graduated, tuition "
                + "FROM student ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Student> students = JDBCTool.resultSetToList(rs, Student.class);

            assertFalse(students.isEmpty());
            Student student = students.get(0);
            assertNotNull(student.getId());
            assertEquals("赵六", student.getName());
            assertEquals("数据科学与大数据技术", student.getMajor());
            assertEquals(19, student.getAge());
            assertEquals(LocalDate.of(2023, 9, 1), student.getEnrollmentDate());
            assertEquals(Boolean.FALSE, student.getGraduated());
            assertEquals(0, new BigDecimal("6200.00").compareTo(student.getTuition()));
        }
    }

    @Test
    @Order(5)
    void testResultSetToListWithPartialColumns() {
        List<Student> students = JDBCTool.query(
                "SELECT id, name FROM student ORDER BY id", Student.class, connection);

        assertFalse(students.isEmpty());
        Student student = students.get(0);
        assertNotNull(student.getName());
        assertNull(student.getMajor());
        assertNull(student.getTuition());
    }

    @Test
    @Order(6)
    void testDelete() {
        College college = new College("数学学院", "MATH");
        JDBCTool.save(college, connection);
        Integer id = college.getId();
        assertNotNull(JDBCTool.getOneById(String.valueOf(id), College.class, connection));

        int rows = JDBCTool.delete(college, connection);

        assertEquals(1, rows);
        assertNull(JDBCTool.getOneById(String.valueOf(id), College.class, connection));
    }

    @Test
    @Order(7)
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
        assertEquals("123", JDBCTool.convertValue(String.class, 123));
    }
}
