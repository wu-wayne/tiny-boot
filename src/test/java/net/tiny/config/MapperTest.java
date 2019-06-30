package net.tiny.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;


public class MapperTest {

    @Test
    public void testConvertMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", new Double(10));
        map.put("name", "Hoge");
        map.put("value", new Double(123.45));

        Mapper mapper = new Mapper();
        SampleBean bean = mapper.convert(map, SampleBean.class);
        assertNotNull(bean);
        assertEquals(10L, bean.getId());
        assertEquals("Hoge", bean.getName());
        assertEquals(123.45f, bean.getValue());
    }


    @Test
    public void testConvertCollection() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> names = Arrays.asList("X","Y","Z");
        List<String> nesteds = Arrays.asList("a","b","c");
        List<String> array = Arrays.asList("1","2","3");
        map.put("names", names);
        map.put("nesteds", nesteds);
        map.put("array", array);

        Mapper mapper = new Mapper();
        CollectionBean bean = mapper.convert(map, CollectionBean.class);
        assertNotNull(bean);
        assertEquals(3, bean.getNames().size());
        assertEquals(3, bean.getNesteds().size());
        assertEquals(3, bean.getArray().length);
        String[] nameSet = bean.getNames().toArray(new String[3]);
        assertEquals("X", nameSet[0]);
        assertEquals("Y", nameSet[1]);
        assertEquals("Z", nameSet[2]);
        assertEquals("a", bean.getNesteds().get(0));
        assertEquals("b", bean.getNesteds().get(1));
        assertEquals("c", bean.getNesteds().get(2));
        assertEquals("1", bean.getArray()[0]);
        assertEquals("2", bean.getArray()[1]);
        assertEquals("3", bean.getArray()[2]);

    }

    public static class SampleBean {
        private long id;
        private String name;
        private Float value;

        public long getId() {
            return id;
        }
        public void setId(long id) {
            this.id = id;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Float getValue() {
            return value;
        }
        public void setValue(Float value) {
            this.value = value;
        }
    }

    public static class CollectionBean {
        private Set<String> names;
        private String[] array;
        private List<String> nesteds;

        public Set<String> getNames() {
            return names;
        }
        public void setNames(Set<String> names) {
            this.names = names;
        }

        public String[] getArray() {
            return array;
        }
        public void setArray(String[] array) {
            this.array = array;
        }

        public List<String> getNesteds() {
            return nesteds;
        }
        public void setNesteds(List<String> nesteds) {
            this.nesteds = nesteds;
        }
    }
}
