import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import static java.time.LocalDateTime.now;

public class PDFParser {

  public static void main(String[] args) throws IOException {
    convertPDFToTxt("gib.pdf");
  }

  public static String convertPDFToTxt(String filePath) throws IOException {
    byte[] thePDFFileBytes = readFileAsBytes(filePath);
    PDDocument pddDoc = PDDocument.load(thePDFFileBytes);
    PDFTextStripper reader = new PDFTextStripper();
    String pageText = reader.getText(pddDoc);
    List<String> lines = Arrays.stream(pageText.split(System.lineSeparator())).toList();
    processData(lines);
    pddDoc.close();
    return pageText;
  }

  private static byte[] readFileAsBytes(String filePath) throws IOException {
    FileInputStream inputStream = new FileInputStream(filePath);
    return IOUtils.toByteArray(inputStream);
  }

  private static void processData(List<String> lines) throws IOException {
    File cityFile = new File("city.txt");
    cityFile.delete();
    File districtFile = new File("district.txt");
    districtFile.delete();
    File taxOfficeFile = new File("tax_office.txt");
    taxOfficeFile.delete();
    if (!cityFile.createNewFile()) {
      System.out.println("city.txt creation failed!");
      System.exit(1);
    }
    if (!districtFile.createNewFile()) {
      System.out.println("district.txt creation failed!");
      System.exit(1);
    }
    if (!taxOfficeFile.createNewFile()) {
      System.out.println("tax_office.txt creation failed!");
      System.exit(1);
    }
    try {
      FileWriter cityWriter = new FileWriter("city.txt");
      FileWriter districtWriter = new FileWriter("district.txt");
      FileWriter taxOfficeWriter = new FileWriter("tax_office.txt");

      Set<String> blacklistLines =
        Set.of("VERGİ DAİRESİ BAŞKANLIKLARI (*) VE VERGİ DAİRELERİ LİSTESİ", "İL İLÇE", "MUH.BİR", ".", "KODU",
               "VERGİ DAİRESİ");

      LocalDateTime current = now();
      Set<String> queries = new java.util.HashSet<>();
      AtomicReference<Integer> cityId = new AtomicReference<>(1);
      AtomicReference<Integer> districtId = new AtomicReference<>(1);
      HashMap<String, Integer> cityNameIdMap = new HashMap<>();
      HashMap<String, Integer> districtNameIdMap = new HashMap<>();
      lines.forEach(line -> {
        if (blacklistLines.contains(line) || line.startsWith(
          "(*) Vergi dairesi ve tahsil dairesi sıfatı olan başkanlıklar")) {
          return;
        }
        List<String> words = Arrays.stream(line.split(" ")).filter(w -> !w.equals("(*)")).toList();
        Integer taxOfficeCodeIndex = 4;
        ListIterator<String> listIterator = words.listIterator(words.size());
        while (listIterator.hasPrevious()) {
          String previous = listIterator.previous();
          if (isNumeric(previous) && previous.length() > 3) { // we found tax office code in reverse
            taxOfficeCodeIndex = listIterator.nextIndex();
            break;
          }
        }

        String cityCode = words.get(1);
        String cityName = words.get(2);
        String districtName = words.get(3);
        String taxOfficeCode = words.get(taxOfficeCodeIndex);
        String taxOfficeName = String.join(" ", words.subList(taxOfficeCodeIndex + 1, words.size()));
        try {
          String cityQuery = MessageFormat.format("""
                                                    INSERT INTO city (code, name, created_at, last_updated_at)
                                                    VALUES (''{0}'', ''{1}'', ''{2}'', ''{3}'');
                                                    """, cityCode, cityName, current, current);
          if (!queries.contains(cityQuery)) {
            cityWriter.write(cityQuery);
            queries.add(cityQuery);
            cityNameIdMap.put(cityName, cityId.get());
            cityId.getAndSet(cityId.get() + 1);
          }

          String districtQuery = MessageFormat.format("""
                                                        INSERT INTO district(city_id, name, created_at, last_updated_at)
                                                        VALUES (''{0}'', ''{1}'', ''{2}'', ''{3}'');
                                                        """, cityNameIdMap.get(cityName), districtName, current,
                                                      current);
          if (!queries.contains(districtQuery)) {
            districtWriter.write(districtQuery);
            queries.add(districtQuery);
            districtNameIdMap.put(districtName, districtId.get());
            districtId.getAndSet(districtId.get() + 1);
          }

          String taxOfficeQuery = MessageFormat.format("""
                                                         INSERT INTO tax_office(
                                                         	city_id, district_id, name, code, created_at, last_updated_at)
                                                         	VALUES (''{0}'', ''{1}'', ''{2}'', ''{3}'', ''{4}'', ''{5}'');
                                                         """, cityNameIdMap.get(cityName),
                                                       districtNameIdMap.get(districtName), taxOfficeName,
                                                       taxOfficeCode, current, current);
          if (!queries.contains(taxOfficeQuery)) {
            taxOfficeWriter.write(taxOfficeQuery);
            queries.add(taxOfficeQuery);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      cityWriter.close();
      districtWriter.close();
      taxOfficeWriter.close();
    } catch (IOException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
  }

  public static boolean isNumeric(String strNum) {
    Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
    if (strNum == null) {
      return false;
    }
    return pattern.matcher(strNum).matches();
  }
}

