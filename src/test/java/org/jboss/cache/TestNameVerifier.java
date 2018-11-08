package org.jboss.cache;

import org.testng.annotations.Test;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.FilenameFilter;
import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Class that tests that test names are correclty set for each test.
 *
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "TestNameVerifier")
public class TestNameVerifier {
   String dir = "src/test/java/org/jboss/cache";

   Pattern packageLinePattern = Pattern.compile("package org.jboss.cache[^;]*");
   Pattern classLinePattern = Pattern.compile("(abstract\\s*)??(public\\s*)(abstract\\s*)??class [^\\s]*");
   Pattern atAnnotationPattern = Pattern.compile("@Test[^)]*");
   Pattern testNamePattern = Pattern.compile("testName\\s*=\\s*\"[^\"]*\"");

   String fileCache;

   FilenameFilter javaFilter = new FilenameFilter() {
      public boolean accept(File dir, String name) {
         if (dir.getAbsolutePath().contains("testng")) return false;
         return name.endsWith(".java");
      }
   };

   FileFilter onlyDirs = new FileFilter() {
      public boolean accept(File pathname) {
         return pathname.isDirectory();
      }
   };

   @Test(enabled = false, description = "Do not enable this unless you want your files to be updated with test names!!!")
   public void process() throws Exception {
      File[] javaFiles = getJavaFiles();
      for (File file : javaFiles) {
         if (needsUpdate(file)) {
            System.out.println("Updating file: " + file.getAbsolutePath());
            updateFile(file);
         }
      }
   }

   private void updateFile(File file) throws Exception {
      String javaString = fileCache;
      String testName = getTestName(javaString, file.getName());
      String testNameStr = ", testName = \"" + testName + "\"";
      javaString = replaceAtTestAnnotation(javaString, testNameStr);
      persistNewFile(file, javaString);
   }

   private void persistNewFile(File file, String javaString) throws Exception {
      if (file.delete()) {
         System.out.println("!!!!!!!!!! error porcessing file " + file.getName());
         return;
      }
      file.createNewFile();
      PrintWriter writter = new PrintWriter(file);
      writter.append(javaString);
      writter.close();
   }

   private String replaceAtTestAnnotation(String javaString, String testNameStr) {
      Matcher matcher = atAnnotationPattern.matcher(javaString);
      boolean found = matcher.find();
      assert found;
      String theMatch = matcher.group();
      return matcher.replaceFirst(theMatch + testNameStr);
   }

   private String getTestName(String javaString, String filename) {
      String classNamePart = getClassNamePart(javaString, filename);

      //abstract classes do not require test names
      if (classNamePart.indexOf("abstract") >= 0) return null;

      classNamePart = classNamePart.substring("public class ".length());
      String packagePart = getPackagePart(javaString, filename);
      //if the test is in org.horizon package then make sure no . is prepanded
      String packagePrepend = ((packagePart != null) && (packagePart.length() > 0)) ? packagePart + "." : "";
      return packagePrepend + classNamePart;
   }

   private String getClassNamePart(String javaString, String filename) {
      Matcher matcher = classLinePattern.matcher(javaString);
      boolean found = matcher.find();
      assert found : "could not determine class name for file: " + filename;
      return matcher.group();
   }

   private String getPackagePart(String javaString, String filename) {
      Matcher matcher = packageLinePattern.matcher(javaString);
      boolean found = matcher.find();
      assert found : "Could not determine package name for file: " + filename;
      String theMatch = matcher.group();
      String partial = theMatch.substring("package org.jboss.cache".length());
      if (partial.trim().length() == 0) return partial.trim();
      return partial.substring(1);//drop the leading dot.
   }


   private boolean needsUpdate(File file) throws Exception {
      String javaFileStr = getFileAsString(file);
      if (javaFileStr.indexOf(" testName = \"") > 0) return false;
      int atTestIndex = javaFileStr.indexOf("@Test");
      int classDeclarationIndex = javaFileStr.indexOf("public class");
      return atTestIndex > 0 && atTestIndex < classDeclarationIndex;
   }

   private String getFileAsString(File file) throws Exception {
      StringBuilder builder = new StringBuilder();
      BufferedReader fileReader = new BufferedReader(new FileReader(file));
      String line;
      while ((line = fileReader.readLine()) != null) {
         builder.append(line + "\n");
      }
      this.fileCache = builder.toString();
//      fileReader.close();
      return fileCache;
   }

   private File[] getJavaFiles() {
      File file = new File(dir);
      assert file.isDirectory();
      ArrayList<File> result = new ArrayList<File>();
      addJavaFiles(file, result);
      return result.toArray(new File[0]);
   }

   private void addJavaFiles(File file, ArrayList<File> result) {
      assert file.isDirectory();
      File[] javaFiles = file.listFiles(javaFilter);
//      printFiles(javaFiles);
      result.addAll(Arrays.asList(javaFiles));
      for (File dir : file.listFiles(onlyDirs)) {
         addJavaFiles(dir, result);
      }
   }

   private void printFiles(File[] javaFiles) {
      for (File f : javaFiles) {
         System.out.println(f.getAbsolutePath());
      }
   }

   public void verifyTestName() throws Exception {
      File[] javaFiles = getJavaFiles();
      StringBuilder errorMessage = new StringBuilder("Following test class(es) do not have an appropriate test name: \n");
      boolean hasErrors = false;
      for (File file : javaFiles) {
         String expectedName = incorrectTestName(file);
         if (expectedName != null) {
            errorMessage.append(file.getAbsoluteFile()).append(" expected test name is: '").append(expectedName).append("' \n");
            hasErrors = true;
         }
      }
      assert !hasErrors : errorMessage.append("The rules for writting UTs are being descibed here: https://www.jboss.org/community/docs/DOC-13315");
   }

   private String incorrectTestName(File file) throws Exception {
      String fileAsStr = getFileAsString(file);

      boolean containsTestAnnotation = atAnnotationPattern.matcher(fileAsStr).find();
      if (!containsTestAnnotation) return null;

      String expectedTestName = getTestName(fileAsStr, file.getName());
      if (expectedTestName == null) return null; //this happens when the class is abstract
      Matcher matcher = this.testNamePattern.matcher(fileAsStr);
      if (!matcher.find()) return expectedTestName;
      String name = matcher.group().trim();
      int firstIndexOfQuote = name.indexOf('"');
      String existingTestName = name.substring(firstIndexOfQuote + 1, name.length() - 1); //to ignore last quote
      if (!existingTestName.equals(expectedTestName)) return expectedTestName;
      return null;
   }


   @Test(enabled = false)
   public static void main(String[] args) throws Exception {
      File file = new File("C:\\jboss\\coding\\za_trunk\\src\\test\\java\\org\\jboss\\cache\\statetransfer\\ForcedStateTransferTest.java");
      String incorrectName = new TestNameVerifier().incorrectTestName(file);
      System.out.println("incorrectName = " + incorrectName);


//      new TestNameVerifier().process();
//      Pattern classLinePattern = Pattern.compile("@Test[^)]*");
//      String totest = "aaaa\n" + "@Test(groups = {\"functional\", \"pessimistic\"})\n" + "{ dsadsadsa";
//      Matcher matcher = classLinePattern.matcher(totest);
//      boolean found = matcher.find();
//      System.out.println("found = " + found);
//      String theMatch = matcher.group();
//      String result = matcher.replaceFirst(theMatch + ", testName=\"alaBala\"");
//      System.out.println("theMatch = " + theMatch);
//      System.out.println("result = " + result);

   }
}