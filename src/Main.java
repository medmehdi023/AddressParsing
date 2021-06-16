import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class Main {

    public static final String WITHOUT_CITY = "address_without_cities";
    public static final String WITH_CITY = "address_with_cities";

    // adresseRamedArabe
    // listAdresseErrachdia

    public static final String INPUT_FILE_NAME = "adresseRamedArabe";


    public static File getFilePath(String fileName) {
        File jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        String propertiesPath = jarPath.getParentFile().getAbsolutePath();
        String path = propertiesPath + "/" + fileName;
        return new File(path);
    }

    public static String insertConcurrency(SortedMap<Integer, String> list, Connection con) {
        try {
            System.out.println("Insert " + Thread.currentThread());
            System.out.println("the lenght of list to insert is " + list.size());
            if (con != null) {
                Statement statement = con.createStatement();

                for (int key : list.keySet()) {
                    statement.executeUpdate("insert into testconcurrency  (id, libelle) values ("+"'"+key+"'"+","+"'"+list.get(key)+"'"+")") ;
                }

                statement.close();
                System.out.println("the lenght of list to insert is AFTER " + list.size());

            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return "ops";
    }

    public static String updateConcurrency(SortedMap<Integer, String> list, Connection con) throws Exception {
        try {
            System.out.println("Size of the list to update is :" + list.size());
            if (con != null) {
                Statement statement = con.createStatement();

                for (int key : list.keySet()) {
                    System.out.println("Update " + Thread.currentThread()+ " key : "+key);
                    statement.executeUpdate("UPDATE addressParsing SET libelle = 'bravo00' where id = " + key + " ");
                }

                statement.close();
                con.close();
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e.getMessage());
        }
        return "ops";
    }

    public static ArrayList<SortedMap<Integer, String>> getShunksOfArray (SortedMap<Integer, String> array, int devideBy) {

        ArrayList<SortedMap<Integer, String>> res = new ArrayList<>();

        int arraySize  = array.size();
        int shunkLength = arraySize / devideBy;
        int I = 0;
        int sub = array.size();
        int rest = 0;

        for (I = 0; I < array.size(); I++) {
            if (I % shunkLength == 0 && I != 0) {
                SortedMap<Integer, String> pp = array.subMap(I-shunkLength+1, I+1);
                sub -= shunkLength;
                rest += shunkLength;
                res.add(pp);
            }
        }
        res.add(array.subMap(rest+1, array.size()+1));
        return res;
    }



    public static String[] getKeysFromDB (String keys){
        ArrayList<String> arraytemp = new ArrayList<>();
        try {
            Connection con = Helper.getConnection();
            ResultSet rs;
            Statement statement = con.createStatement();
            System.out.println("SELECT * FROM "+""+keys+""+"");
            rs = statement.executeQuery("SELECT * FROM "+keys+"");
            while ( rs.next() ) {
                arraytemp.add(rs.getString("libelle"));
            }
            con.close();
        }catch (Exception e) {
            System.out.println(e.getMessage());
        }

        String keyArrays[] = new String[arraytemp.size()];
        return arraytemp.toArray(keyArrays);
    }




    public static void main(String[] args) {


//        Connection con = Helper.getConnection();
//        File fileSource = getFilePath(args[0]);
//        File fileDestination = getFilePath(args[1]);
//        File citiesFile = getFilePath(args[2]);


        String cities[] = Helper.getCitiesListFromDb();

        String topLevel[] = getKeysFromDB("top_level_keys");
        String secondLevel[] = getKeysFromDB("second_level_keys");
        String delimiter[] = getKeysFromDB("delimiter_keys");

        SortedMap<Integer, String> test = Helper.getAllInitialAddressFromDB("source_adr");

        System.out.println("topLevel "+topLevel.length);
        System.out.println("secondLevel "+secondLevel.length);
        System.out.println("delimiter "+delimiter.length);
        System.out.println("all address size :"+test.size());

        // TODO get this value from main input
        ArrayList<SortedMap<Integer, String>> res = getShunksOfArray(test, 4);

        ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool() ;// Executors.newFixedThreadPool(10); //;

        List<Future<Integer>> resultList = new ArrayList<>();

        // Create a list of shunk from db array
        for(int k = 0 ; k < res.size() ; k++){

            ParseAddress t = new ParseAddress(res.get(k), cities, topLevel, secondLevel, delimiter);
            Future<Integer> result = executor.submit(t);
            resultList.add(result);
        }

        // Catch error thrown from task submited
        for (int i = 0; i < resultList.size(); i++)
        {
            Future<Integer> result = resultList.get(i);
            Integer number = null;
            try {
                number = result.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            System.out.printf("Main: Task %d: %d\n", i, number);
        }

        executor.shutdown();




    }




    private static boolean WriteToFile(XSSFSheet mySheet, XSSFWorkbook wb, File file, int rowNumber, String adrToWrite, int column, boolean color) {
        boolean saved = false;
        try {
            Row row = mySheet.getRow(rowNumber);
            Cell cell = CellUtil.getCell(row, column);
            cell.setCellValue(adrToWrite);
            CellStyle style = wb.createCellStyle();
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            if (color) {
                style.setFillForegroundColor(IndexedColors.RED.getIndex());

            } else {
                style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            }
            cell.setCellStyle(style);

            FileOutputStream os = new FileOutputStream(file);
            wb.write(os);
            saved = true;
        } catch (Exception e) {

        }
        return saved;
    }

    private static String findDelimiterAndGetAdrPortion(String address, String levelKeys[], String delimiters[]) {

        String foundKey = Helper.extractKey(address, levelKeys);

        if (!foundKey.equals("-1")) {

            int foundKeyLength = foundKey.length();
            int foundKeyIndex = address.indexOf(foundKey);
            String secondPart = address.substring(foundKeyIndex + foundKeyLength);
            int stopIndex = secondPart.length();
            String foundDelimiter = Helper.extractKey(secondPart, delimiters);

            if (!foundDelimiter.equals("-1")) {
                stopIndex = secondPart.indexOf(foundDelimiter);
            }

            return foundKey + "" + secondPart.substring(0, stopIndex);
        } else {
            return "-404";
        }

    }

    public static class ParseAddress implements Callable<Integer> {

        private SortedMap<Integer, String> list;
        private String[] cities;
        private String[] topLevelKeys;
        private String[] secondLevelKeys;
        private String[] allkeys;
        private Connection con;

        public ParseAddress(SortedMap<Integer, String> list, String[] cities, String[] topLevelKeys, String[] secondLevelKeys, String[] allkeys){
            this.list = list;
            this.cities = cities;
            this.topLevelKeys = topLevelKeys;
            this.secondLevelKeys = secondLevelKeys;
            this.allkeys = allkeys;
            this.con = Helper.getConnection();
        }

        // standard constructors

        public Integer call() throws Exception {

            System.out.println("===== start =====");
            System.out.println("First key : " +list.firstKey()+" Last key : "+list.lastKey());
            System.out.println("CALL START : "+Thread.currentThread().getName() +" With size : "+list.size());
            System.out.println("===== end ===== \n\n");

            try {

                Helper.removeCitiesFromAdr(list, cities, allkeys, con);

                System.out.println("*******************************************************************************");
                insertParsedPartAddress(list, topLevelKeys, secondLevelKeys, allkeys);


            }catch (Exception e) {
                e.printStackTrace();
                throw new Exception(e.getMessage());
            }
            System.out.println("CALL END : "+Thread.currentThread().getName());
            return 0;
        }
    }

    public static void insertParsedPartAddress(SortedMap<Integer, String> list, String[] toplevelKeys, String[] secondLevelKeys, String[] delimiterKeys){

        SortedMap<Integer, String> myList = new TreeMap<>(list);

        for (Map.Entry<Integer, String> entry : myList.entrySet()) {

            int id = entry.getKey();
            String addresse = entry.getValue();

            String firstLevelPartOR404 = findDelimiterAndGetAdrPortion(addresse, toplevelKeys, delimiterKeys);
            System.out.println(" ====>>address ID : "+id);

            if (!firstLevelPartOR404.equals("-404")) {

                Helper.insertTopLevelPartInDB(id, firstLevelPartOR404);

                String secondLevelPartOR404 = findDelimiterAndGetAdrPortion(addresse, secondLevelKeys, delimiterKeys);

                if (!secondLevelPartOR404.equals("-404")) {

                    Helper.insertSecondLevelPartInDB(id, secondLevelPartOR404);

                } else {
                    //System.out.println("Second level Not found");
                }

            } else {
                String secondLevelPartOR404 = findDelimiterAndGetAdrPortion(addresse, secondLevelKeys, delimiterKeys);
                if (!secondLevelPartOR404.equals("-404")) {

                    Helper.insertSecondLevelPartInDB(id, secondLevelPartOR404);

                } else {
                    Helper.insertTopLevelPartInDB(id, "TO BE HANDLED MANUALLY");
                }
            }

        }

    }


}
