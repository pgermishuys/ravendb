package raven.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.codehaus.jackson.JsonParseException;
import org.junit.Test;

import raven.client.json.Guid;
import raven.client.json.JTokenType;
import raven.client.json.RavenJArray;
import raven.client.json.RavenJObject;
import raven.client.json.RavenJToken;
import raven.client.json.RavenJValue;
import raven.client.json.lang.JsonWriterException;

public class RavenJObjectsTest {

  static class Company {
    private String name;
    private List<Person> employees;

    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }
    public List<Person> getEmployees() {
      return employees;
    }
    public void setEmployees(List<Person> employees) {
      this.employees = employees;
    }
  }

  static class Person {
    private String name;
    private String surname;
    private int[] types;


    public int[] getTypes() {
      return types;
    }
    public void setTypes(int[] types) {
      this.types = types;
    }
    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }
    public String getSurname() {
      return surname;
    }
    public void setSurname(String surname) {
      this.surname = surname;
    }
  }

  @Test
  public void testRavenJValue() throws Exception {
    RavenJValue stringValue = new RavenJValue("this is string");
    assertEquals("this is string", stringValue.getValue());
    assertEquals(JTokenType.STRING, stringValue.getType());

    RavenJValue intValue = new RavenJValue(5);
    assertEquals(Integer.valueOf(5), intValue.getValue());
    assertEquals(JTokenType.INTEGER, intValue.getType());

    RavenJValue longValue = new RavenJValue(5L);
    assertEquals(Long.valueOf(5), longValue.getValue());
    assertEquals(JTokenType.INTEGER, longValue.getType());

    RavenJValue doubleValue = new RavenJValue((double)12.23f);
    assertEquals(Double.valueOf(12.23f), doubleValue.getValue());
    assertEquals(JTokenType.FLOAT, doubleValue.getType());

    RavenJValue floatValue = new RavenJValue((float)12.23f);
    assertEquals(12.23f, (float)floatValue.getValue(), 0.001f);
    assertEquals(JTokenType.FLOAT, floatValue.getType());

    RavenJValue booleanValue = new RavenJValue(true);
    assertTrue((boolean)booleanValue.getValue());
    assertEquals(JTokenType.BOOLEAN, booleanValue.getType());

  }

  @Test
  public void testRavenJValueTypeDetection() throws Exception {
    Object o = null;
    assertEquals(JTokenType.NULL, new RavenJValue(o).getType());
    assertEquals(JTokenType.STRING, new RavenJValue("string").getType());
    assertEquals(JTokenType.INTEGER, new RavenJValue(JTokenType.BOOLEAN).getType());
    assertEquals(JTokenType.FLOAT, new RavenJValue(Float.valueOf(12.34f)).getType());
    assertEquals(JTokenType.FLOAT, new RavenJValue(Double.valueOf(12.34f)).getType());
    assertEquals(JTokenType.DATE, new RavenJValue(new Date()).getType());
    assertEquals(JTokenType.BOOLEAN, new RavenJValue(Boolean.FALSE).getType());
    assertEquals(JTokenType.STRING, new RavenJValue(new URI("http://ravendb.net")).getType());
    assertEquals(JTokenType.STRING, new RavenJValue(new Guid("123")).getType());
    assertEquals(JTokenType.BYTES, new RavenJValue("test".getBytes()).getType());
    assertEquals(JTokenType.DATE, new RavenJValue(new Date()).getType());
  }

  @Test
  public void testHashCodeAndEqual() throws Exception {
    //TODO implemenent me
  }

  @Test
  public void testClone() throws Exception {
    RavenJValue value1 = new RavenJValue("raven is cool");
    RavenJValue clonedValue = value1.cloneToken();
    assertFalse(value1 == clonedValue); //yes, we compare using ==
    assertTrue(value1.getValue() == clonedValue.getValue());
    assertEquals(value1.getType(), clonedValue.getType());
  }

  @Test
  public void testSnapshots() throws Exception {
    RavenJValue value1=  new RavenJValue("test");
    assertFalse(value1.isSnapshot());
    value1.ensureCannotBeChangeAndEnableShapshotting();
    assertTrue(value1.isSnapshot());
  }

  @Test(expected = IllegalStateException.class)
  public void testSetValueOnSnapshot()  {
    RavenJValue value1=  new RavenJValue("test");
    value1.ensureCannotBeChangeAndEnableShapshotting();
    value1.setValue("aa");
  }

  @Test
  public void testSetValue() {
    RavenJValue value1 =  new RavenJValue("test");
    value1.setValue("test2");
    assertEquals(JTokenType.STRING, value1.getType());

    value1.setValue(null);
    assertEquals(JTokenType.NULL, value1.getType());

    value1.setValue("test");
    assertEquals(JTokenType.STRING, value1.getType());

    value1.setValue(new Guid("1234-1234-00"));
    assertEquals(JTokenType.GUID, value1.getType());

    value1.setValue(new Date());
    assertEquals(JTokenType.DATE, value1.getType());

    value1.setValue(12.2f);
    assertEquals(JTokenType.FLOAT, value1.getType());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetValueException() {
    RavenJValue value1 = RavenJValue.getNull();
    value1.setValue(Calendar.getInstance());
  }

  @Test
  public void createSnapshot() {
    RavenJValue value = new RavenJValue("test");
    try {
      value.createSnapshot();
    } catch (IllegalStateException e) {
      // expected
    }
    value.ensureCannotBeChangeAndEnableShapshotting();
    RavenJValue snapshot = value.createSnapshot();
    snapshot.setValue("test2");
    assertEquals("Snapshot should not be changed!", "test", value.getValue());
  }


  @Test
  public void testParser() throws JsonParseException, IOException {
    innerTestParseRavenJValue("null", JTokenType.NULL, null);
    innerTestParseRavenJValue("true", JTokenType.BOOLEAN, true);
    innerTestParseRavenJValue("false", JTokenType.BOOLEAN, false);
    innerTestParseRavenJValue("\"ala\"", JTokenType.STRING, "ala");
    innerTestParseRavenJValue("12", JTokenType.INTEGER, 12);
    innerTestParseRavenJValue("12.5", JTokenType.FLOAT, Double.valueOf(12.5f));
  }

  @Test
  public void testInitializeRavenJArray() {
    RavenJValue value1 = new RavenJValue("value1");
    RavenJValue value2 = new RavenJValue(5);
    RavenJValue value3 = new RavenJValue(2.5f);
    RavenJValue value4 = new RavenJValue(false);
    RavenJValue value5 = RavenJValue.getNull();

    RavenJArray array = new RavenJArray(value1, value2, value3, value4, value5);

    assertEquals(5, array.size());
    assertEquals(value3, array.get(2)); //get is 0-based

    RavenJArray emptyArray = new RavenJArray();
    assertEquals(0, emptyArray.size());

    array.set(3, new RavenJValue(true));

  }

  @Test
  public void testParseJArray() {
    String array1String = "[\"value1\", 5, false, null]";
    RavenJArray ravenJArray = RavenJArray.parse(array1String);

    assertEquals(4, ravenJArray.size());

    assertNotNull(ravenJArray.get(0));
    assertNotNull(ravenJArray.get(1));
    assertNotNull(ravenJArray.get(2));
    assertNotNull(ravenJArray.get(3));

    assertEquals(JTokenType.NULL, ravenJArray.get(3).getType());


    String arrayOfArray = "[ [1,2,3], false, [null, null, \"test\"]  ]";
    RavenJArray array2 = RavenJArray.parse(arrayOfArray);

    assertEquals(3, array2.size());
    assertEquals(3, ((RavenJArray) array2.get(0)).size());
    assertEquals(3, ((RavenJArray) array2.get(2)).size());

  }

  @Test
  public void testParseInvalidArray() {
    try {
      RavenJArray.parse("[ 1,2");
      fail("it was invalid array!");
    } catch (Exception e) { /* ok */ }

    try {
      RavenJArray.parse("1");
      fail("it wasn't array!");
    } catch (Exception e) { /* ok */ }
  }

  @Test
  public void testArraySnapshot() {
    RavenJArray array = new RavenJArray(new RavenJValue(false), new RavenJValue("5"));
    assertFalse(array.isSnapshot());
    array.ensureCannotBeChangeAndEnableShapshotting();
    assertTrue(array.isSnapshot());
    try {
      array.add(new RavenJValue(5.5f));
      fail("Array was locked - we can add elemenets");
    } catch (Exception e) { /* ok */ }
    RavenJArray snapshot = array.createSnapshot();
    snapshot.add(RavenJValue.getNull());
    assertEquals(3, snapshot.size());

  }

  @Test
  public void testArrayClone() {
    RavenJArray array = new RavenJArray(new RavenJArray(new RavenJValue(5l), new RavenJValue(7l), new RavenJValue(false)));
    RavenJArray clonedToken = array.cloneToken();
    // now modify original object
    ((RavenJArray) array.get(0)).add(new RavenJValue(true));
    assertEquals(4, ((RavenJArray) array.get(0)).size());
    assertEquals(3, ((RavenJArray) clonedToken.get(0)).size());

    // now clone array with nulls

    array = new RavenJArray(RavenJValue.getNull(), RavenJValue.getNull(), new RavenJValue(true));
    clonedToken = array.cloneToken();
    assertEquals(3, clonedToken.size());
    assertEquals(JTokenType.NULL, array.get(0).getType());


  }

  private void innerTestParseRavenJValue(String input, JTokenType expectedTokenType, Object expectedValue) throws JsonParseException, IOException {
    RavenJToken ravenJToken = RavenJToken.parse(input);
    RavenJValue ravenJValue = (RavenJValue) ravenJToken;

    assertEquals(expectedTokenType, ravenJValue.getType());
    assertEquals(expectedValue, ravenJValue.getValue());

  }


  @Test
  public void testToString() {
    Date date = mock(Date.class);
    when(date.toString()).thenReturn("1234");
    RavenJValue value = new RavenJValue(date);
    assertEquals("1234", value.toString());

    assertEquals("", RavenJValue.getNull().toString());
  }

  @Test
  public void testRavenJObjectFromObject() throws JsonWriterException {
    Person person1 = new Person();
    person1.setName("Joe");
    person1.setSurname("Doe");
    person1.setTypes(new int[] { 1,2,3,4,5 });

    RavenJObject ravenJObject = RavenJObject.fromObject(person1);
    assertNotNull(ravenJObject);
    System.err.println(ravenJObject);

  }
}
