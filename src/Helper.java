import com.monitorjbl.xlsx.StreamingReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class Helper {
    public static String getKeyRegex(String[] keys) {
        String keyRegex = String.join("|", (CharSequence[])keys);
        return "\\b(" + keyRegex + ")\\b";
    }

    public static String extractKey(String addressString, String[] keys) {
        Matcher m = Pattern.compile(getKeyRegex(keys)).matcher(addressString);
        if (m.find())
            return m.group(0);
        return "-1";
    }

    public static String[] getCities(File citiesFile) {
        List<String> cities = new LinkedList<>();
        try {
            FileInputStream fis = new FileInputStream(citiesFile);
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            Iterator<Row> itr = sheet.iterator();
            XSSFSheet mySheet = wb.getSheetAt(0);
            int rownum = 0;
            int counetr = 0;
            while (itr.hasNext()) {
                counetr++;
                Row row = itr.next();
                Iterator<Cell> cellIterator = row.cellIterator();
                Cell cell = CellUtil.getCell(row, 0);
                switch (cell.getCellType()) {
                    case 1:
                        cities.add(cell.getStringCellValue());
                    case 0:
                        cities.add(String.valueOf(cell.getNumericCellValue()));
                }
            }
        } catch (Exception exception) {}
        String[] myArray = new String[cities.size()];
        cities.toArray(myArray);
        return myArray;
    }

    public static HashMap<Integer, String> getAddress(File fileToLoadFrom) {
        HashMap<Integer, String> adressDict = new HashMap<>();
        try {
            FileInputStream fis = new FileInputStream(fileToLoadFrom);
            XSSFWorkbook wb = new XSSFWorkbook(fis);
            XSSFSheet sheet = wb.getSheetAt(0);
            Iterator<Row> itr = sheet.iterator();
            XSSFSheet mySheet = wb.getSheetAt(0);
            int rownum = 0;
            int counetr = 0;
            while (itr.hasNext()) {
                counetr++;
                Row row = itr.next();
                Iterator<Cell> cellIterator = row.cellIterator();
                Cell cell = CellUtil.getCell(row, 0);
                switch (cell.getCellType()) {
                    case 1:
                        adressDict.put(Integer.valueOf(row.getRowNum()), cell.getStringCellValue());
                    case 0:
                        adressDict.put(Integer.valueOf(row.getRowNum()), String.valueOf(cell.getNumericCellValue()));
                }
            }
        } catch (Exception exception) {}
        return adressDict;
    }

    public static String stringContainsItemFromList(String inputStr, String[] items) {
        Optional<String> answer = Arrays.<String>stream(items).filter(inputStr::contains).findAny();
        return answer.orElse("-1");
    }

    public static String removeCityIfFound(String inputStr, String[] items) {
        List<String> cities = Arrays.asList(items);
        Optional<String> answer = cities.stream().filter(inputStr::contains).filter(inputStr::endsWith).findAny();
        return answer.orElse("-1");
    }

    public static String normalizeAddress(String inputStr, String[] items) {
        String output = inputStr;
        for (String key : items)
            output = output.replaceAll(key, " " + key + " ");
        output = output.replaceAll("\\s+", " ");
        output = output.replaceAll("'", "''");
        output = output.replaceAll(";", "");
        return output;
    }

    public static String replaceLast(String find, String replace, String string) {
        int lastIndex = string.lastIndexOf(find);
        if (lastIndex == -1)
            return string;
        String beginString = string.substring(0, lastIndex);
        String endString = string.substring(lastIndex + find.length());
        return beginString + replace + endString;
    }

    public static String[] getLevelKeys(String fileName) {
        ArrayList<String> holer = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream(fileName + ".txt")));
            String line;
            while ((line = reader.readLine()) != null)
                holer.add(line);
            String[] keyArr = new String[holer.size()];
            return holer.<String>toArray(keyArr);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    public static void removeCitiesFromAdr(SortedMap<Integer, String> list, String[] cities, String[] allKeys, Connection con) throws Exception {
        SortedMap<Integer, String> myList = new TreeMap<>(list);
        try {
            if (con != null) {
                Statement statement = con.createStatement();
                for (Map.Entry<Integer, String> entry : myList.entrySet()) {
                    int id = ((Integer)entry.getKey()).intValue();
                    String addresse = entry.getValue();
                    String normalizedAdr = normalizeAddress(addresse, allKeys);
                    String newAdr = "";
                    String adrrToSave = "";
                    String foundCity = removeCityIfFound(normalizedAdr, cities);
                    if (!foundCity.equals("-1")) {
                        newAdr = replaceLast(foundCity, "", normalizedAdr);
                    } else {
                        newAdr = normalizedAdr;
                    }
                    if (isProbablyArabic(newAdr)) {
                        adrrToSave = "N'" + newAdr + "'";
                    } else {
                        adrrToSave = "'" + newAdr + "'";
                    }
                    statement.executeUpdate("UPDATE addressParsing SET normalized_adr = " + adrrToSave + " where id = " + id + "");
                }
                System.out.println("\n\t======> Finish removing Cities <=======\t\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception(e.getMessage());
        }
    }

    public static boolean isProbablyArabic(String s) {
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            if (c >= 1536 && c <= 1760)
                return true;
            i += Character.charCount(c);
        }
        return false;
    }

    public static Connection getConnection() {
        String connectionUrl = "jdbc:sqlserver://localhost:1433;databaseName=addressParsing;user=developerlogin;password=123456";
        Connection con = null;
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            con = DriverManager.getConnection(connectionUrl);
        } catch (Exception e) {
            return null;
        }
        return con;
    }

    public static String[] getCitiesListFromDb() {
        ArrayList<String> arraytemp = new ArrayList<>();
        try {
            Connection con = getConnection();
            Statement statement = con.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM Cities");
            while (rs.next())
                arraytemp.add(rs.getString("libelle"));
            con.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        String[] citiesList = new String[arraytemp.size()];
        return arraytemp.<String>toArray(citiesList);
    }

    public static List<String> readXlsxToArrayList() {
        File file = new File("C:\\Users\\MOHAMED\\Desktop\\AddressParser\\target\\classes\\com\\taurenk\\addressparser\\mapper\\adresseRabatKenitra.xlsx");
        List<String> adrs = new ArrayList<>();
        try {
            InputStream is = new FileInputStream(file);
            Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(is);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> itr = sheet.iterator();
            int rownum = 0;
            int counetr = 0;
            while (itr.hasNext()) {
                counetr++;
                System.out.println(counetr);
                Row row = itr.next();
                Iterator<Cell> cellIterator = row.cellIterator();
                Cell cell = CellUtil.getCell(row, 4);
                System.out.println(cell.getStringCellValue());
                adrs.add(cell.getStringCellValue());
            }
        } catch (Exception exception) {}
        return adrs;
    }

    public static void saveAddressToDb(ArrayList<String> adrs) {
        Connection con = getConnection();
        try {
            if (con != null) {
                int contr = 0;
                Statement statement = con.createStatement();
                for (String adr : adrs) {
                    contr++;
                    Statement statementt = con.createStatement();
                    System.out.println(adr + " counter " + contr + "\n");
                    String part = "";
                    adr = adr.replaceAll("'", "''");
                    System.out.println("aadddrrr " + adr);
                    adr = adr.replaceAll(";", "");
                    if (isProbablyArabic(adr)) {
                        part = "(N'" + adr + "', 0)";
                    } else {
                        part = "('" + adr + "', 0)";
                    }
                    statement.executeUpdate("INSERT INTO addressParsing (source_adr,was_parsed) values " + part);
                }
                statement.close();
                con.close();
            } else {
                System.out.println("Error creating a connection with the server to insert cities from xlsx");
            }
        } catch (Exception e) {
            System.out.println("error inserting cities");
            System.out.println(e.getMessage());
        }
    }

    public static void insertCitiesInDb() {
        File file = new File("C:\\Users\\MOHAMED\\Desktop\\AddressParser\\target\\classes\\com\\taurenk\\addressparser\\mapper\\cities.xlsx");
        String[] cities = getCities(file);
        Connection con = getConnection();
        try {
            if (con != null) {
                Statement statement = con.createStatement();
                for (String city : cities)
                    statement.addBatch("INSERT INTO cities (libelle) values (N'" + city + "')");
                statement.executeBatch();
                statement.close();
                con.close();
            } else {
                System.out.println("Error creating a connection with the server to insert cities from xlsx");
            }
        } catch (Exception e) {
            System.out.println("error inserting cities");
        }
        System.out.println(cities.length);
    }

    public static void insertKeysToDB() {
        Connection con = getConnection();
        String[] topLevel = getLevelKeys("top_level_keys");
        String[] secondLevel = getLevelKeys("second_level_keys");
        String[] delimiter = getLevelKeys("delimiters");
        try {
            System.out.println("Insert " + Thread.currentThread());
            System.out.println("the lenght of list to insert is " + topLevel.length);
            if (con != null) {
                Statement statement = con.createStatement();
                for (String key : topLevel)
                    statement.executeUpdate("insert into top_level_keys  ( libelle) values (N'" + key + "')");
                statement.close();
                System.out.println("the lenght of list to insert is AFTER " + topLevel.length);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        try {
            System.out.println("Insert " + Thread.currentThread());
            System.out.println("the lenght of list to insert is " + secondLevel.length);
            if (con != null) {
                Statement statement = con.createStatement();
                for (String key : secondLevel)
                    statement.executeUpdate("insert into second_level_keys  ( libelle) values (N'" + key + "')");
                statement.close();
                System.out.println("the lenght of list to insert is AFTER " + secondLevel.length);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        try {
            System.out.println("Insert " + Thread.currentThread());
            System.out.println("the lenght of list to insert is " + delimiter.length);
            if (con != null) {
                Statement statement = con.createStatement();
                for (String key : delimiter)
                    statement.executeUpdate("insert into delimiter_keys  ( libelle) values (N'" + key + "')");
                statement.close();
                System.out.println("the lenght of list to insert is AFTER " + delimiter.length);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static SortedMap<Integer, String> getAllInitialAddressFromDB(String adrColumn) {
        SortedMap<Integer, String> adrArray = new TreeMap<>();
        try {
            Connection con = getConnection();
            Statement statement = con.createStatement();
            ResultSet rs = statement.executeQuery("SELECT id, " + adrColumn + " FROM addressParsing");
            while (rs.next())
                adrArray.put(Integer.valueOf(rs.getInt("id")), rs.getString("source_adr"));
            con.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return adrArray;
    }

    public static boolean insertTopLevelPartInDB(int id, String topLevelPart) {
        Connection con = getConnection();
        boolean updated = false;
        try {
            if (con != null) {
                Statement statement = con.createStatement();
                String partToSave = "";
                if (isProbablyArabic(topLevelPart)) {
                    partToSave = "N'" + topLevelPart + "'";
                } else {
                    partToSave = "'" + topLevelPart + "'";
                }
                statement.executeUpdate("UPDATE addressParsing SET top_level_part = " + partToSave + " WHERE id = " + id + "");
                statement.close();
                con.close();
            } else {
                System.out.println("Error creating a connection with the server to insert cities from xlsx");
            }
            updated = true;
            con.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("error inserting cities");
        }
        return updated;
    }

    public static boolean insertSecondLevelPartInDB(int id, String secondLevelPart) {
        Connection con = getConnection();
        boolean updated = false;
        try {
            if (con != null) {
                Statement statement = con.createStatement();
                String partToSave = "";
                if (isProbablyArabic(secondLevelPart)) {
                    partToSave = "N'" + secondLevelPart + "'";
                } else {
                    partToSave = "'" + secondLevelPart + "'";
                }
                statement.executeUpdate("UPDATE addressParsing SET second_level_part = " + partToSave + " WHERE id = " + id + "");
                statement.close();
                con.close();
            } else {
                System.out.println("Error creating a connection with the server to insert cities from xlsx");
            }
            updated = true;
            con.close();
        } catch (Exception e) {
            System.out.println("error inserting cities");
        }
        return updated;
    }
}
