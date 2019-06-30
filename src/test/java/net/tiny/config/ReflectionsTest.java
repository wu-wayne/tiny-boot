package net.tiny.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReflectionsTest {

    @Test
    public void testGetGenericType() throws Exception {
        Field field = SampleConfig.class.getDeclaredField("list");
        assertNotNull(field);
        Class<?> type = Reflections.getFieldGenericType(field);
        assertTrue(type.isAssignableFrom(String.class));
        assertTrue(String.class.equals(type));
    }

    @Test
    public void testIsAnyType() throws Exception {
        assertTrue(Reflections.isJavaType(String.class));
        assertTrue(Reflections.isJavaType(int.class));
        assertTrue(Reflections.isJavaType(Long.class));
        assertTrue(Reflections.isJavaType(boolean.class));
        assertTrue(Reflections.isJavaType(Boolean.class));
        assertTrue(Reflections.isJavaType(LocalDateTime.class));
        assertTrue(Reflections.isJavaType(Timestamp.class));
        assertFalse(Reflections.isJavaType(DummyBean.class));

        assertTrue(Reflections.isJavaArrayType(int[].class));
        assertTrue(Reflections.isJavaArrayType(Long[].class));
        assertTrue(Reflections.isJavaArrayType(boolean[].class));
        assertTrue(Reflections.isJavaArrayType(Boolean[].class));
        assertTrue(Reflections.isJavaArrayType(LocalDateTime[].class));
        assertTrue(Reflections.isJavaArrayType(Timestamp[].class));
        assertTrue(Reflections.isJavaArrayType(DummyBean[].class));

        assertFalse(Reflections.isJavaArrayType(int.class));

        assertTrue(Reflections.isCollectionType(List.class));
        assertTrue(Reflections.isCollectionType(Set.class));

        assertFalse(Reflections.isCollectionType(Map.class));
    }

    @Test
    public void testIsAssignable() throws Exception {
        assertTrue(Reflections.isAssignable(SampleConfig.class, Cost.class));
        assertTrue(Reflections.isAssignable(AbstractConfig.class, Cost.class));
        assertTrue(Reflections.isAssignable(SampleConfig.class, Serializable.class));
        assertTrue(Reflections.isAssignable(AbstractConfig.class, Serializable.class));
        assertFalse(Reflections.isAssignable(SampleConfig.class, DummyOne.class));

        assertTrue(Reflections.isAssignable(DummyBean.class, Serializable.class));
        assertTrue(Reflections.isAssignable(DummyBean.class, DummyOne.class));
    }


    @Test
    public void testIsInnerClass() throws Exception {
        assertTrue(Reflections.isInnerClass(Cost.class));
        assertTrue(Reflections.isInnerClass(Lasted.class));
        assertFalse(Reflections.isInnerClass(Reflections.class));
    }

    @Test
    public void testHasMainMethod() throws Exception {
        assertTrue(Reflections.hasMainMethod(DummyMain.class));
        assertFalse(Reflections.hasMainMethod(DummyBean.class));
    }

    @Test
    public void testGetSuperClasses() throws Exception {
        assertEquals(0, Reflections.getSuperClasses(DummyMain.class).size());
        assertEquals("java.lang.Object",
                Reflections.getSuperClasses(DummyMain.class, true).get(0).getName());
        assertEquals(1, Reflections.getSuperClasses(SampleConfig.class).size());
        assertEquals("AbstractConfig",
                Reflections.getSuperClasses(SampleConfig.class, true).get(0).getSimpleName());
    }

    @Test
    public void testGetInterfaces() throws Exception {
        assertEquals(2, Reflections.getInterfaces(SampleConfig.class, true, true, true).size());
        assertEquals(1, Reflections.getInterfaces(SampleConfig.class).size());
        assertEquals("Cost",
                Reflections.getInterfaces(SampleConfig.class, true, true, true).get(0).getSimpleName());
        assertEquals(2, Reflections.getInterfaces(DummyBean.class).size());
    }

    @Test
    public void testIsAbstractClass() throws Exception {
        assertTrue(Reflections.isAbstractClass(AbstractConfig.class));
        assertFalse(Reflections.isAbstractClass(SampleConfig.class));
    }

    public static interface DummyZero {

    }
    public static interface DummyOne {
        void main(String[] args);
    }
    public static class DummyMain {
        public static void main(String[] args) {

        }
    }

    public static class DummyBean implements DummyZero, DummyOne, Serializable {
        public void main(String[] args) {
        }
    }

    public static class Lasted implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Nested implements Serializable {
        private static final long serialVersionUID = 1L;
        private Lasted lasted;
        private String name;
        private BigDecimal threshold;

        public Lasted getLasted() {
            return lasted;
        }
        public void setLasted(Lasted lasted) {
            this.lasted = lasted;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public BigDecimal getThreshold() {
            return threshold;
        }
        public void setThreshold(BigDecimal threshold) {
            this.threshold = threshold;
        }
    }

    public static interface Cost {
        Integer getCost();
        void setCost(Integer cost);
    }

    public static abstract class AbstractConfig implements Cost, Serializable {
        private String url;
        private Integer cost;
        public String getUrl() {
            return url;
        }
        public void setUrl(String url) {
            this.url = url;
        }
        public Integer getCost() {
            return cost;
        }
        public void setCost(Integer cost) {
            this.cost = cost;
        }
    }

    public static class SampleConfig extends AbstractConfig {
        private LocalDate date;
        private LocalTime time;
        private LocalDateTime datetime;
        private List<String> list;
        private Nested nested;

        public LocalDate getDate() {
            return date;
        }
        public void setDate(LocalDate date) {
            this.date = date;
        }
        public LocalTime getTime() {
            return time;
        }
        public void setTime(LocalTime time) {
            this.time = time;
        }
        public LocalDateTime getDatetime() {
            return datetime;
        }
        public void setDatetime(LocalDateTime datetime) {
            this.datetime = datetime;
        }
        public List<String> getList() {
            return list;
        }
        public void setList(List<String> list) {
            this.list = list;
        }
        public Nested getNested() {
            return nested;
        }
        public void setNested(Nested nested) {
            this.nested = nested;
        }
    }
}
