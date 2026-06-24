package de.edirom.meigarage.verovio;

import pl.psnc.dl.ege.configuration.EGEConfigurationManager;
import pl.psnc.dl.ege.exception.ConverterException;
import pl.psnc.dl.ege.types.ConversionActionArguments;
import pl.psnc.dl.ege.types.DataType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class VerovioConverterTest {
    private VerovioConverter converter;

    @org.junit.Before
    public void setUp() throws Exception {
        converter = new VerovioConverter();
    }

    @org.junit.After
    public void tearDown() throws Exception {
        converter = null;
    }

    @org.junit.Test
    public void convert() throws IOException, ConverterException {
        InputStream is = new FileInputStream("src/test/resources/test-input.mei.zip");
        OutputStream os = new FileOutputStream("src/test/resources/output.svg.zip");
        //OutputStream os = new FileOutputStream("src/test/resources/output.pdf.zip");
        DataType inputType = new DataType("mei51","text/xml");
        //DataType outputType = new DataType("pdf-verovio","application/pdf");
        DataType outputType = new DataType("svg","image/svg+xml");
        ConversionActionArguments conversionActionArguments = new ConversionActionArguments(inputType, outputType, null);
        String tempDir = "src/test/temp";
        converter.convert(is, os, conversionActionArguments, tempDir);
        assertNotNull(new File("src/test/resources/output.svg.zip"));
        InputStream isout = new FileInputStream("src/test/resources/output.svg.zip");
        File folder = new File("src/test/resources/");
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                System.out.println(file.getName());
            }
        }
        EGEConfigurationManager.getInstance().getStandardIOResolver().decompressStream(isout, new File("src/test/resources/output.svg"));
        File folder2 = new File("src/test/resources/output.svg");
        listOfFiles = folder2.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                System.out.println("####");
                System.out.println(file.getName());
            }
        }
        assertEquals("Files differ",
                new String(Files.readAllBytes(Paths.get("src/test/resources/expected-output.svg"))).replaceAll("id=\"[\\s\\S]*?\"|xlink:href=\"[\\s\\S]*?\"|class=\"[\\s\\S]*?\"",""),
                new String(Files.readAllBytes(Paths.get("src/test/resources/output.svg/output_001.svg"))).replaceAll("id=\"[\\s\\S]*?\"|xlink:href=\"[\\s\\S]*?\"|class=\"[\\s\\S]*?\"",""));
        is.close();
        os.close();
        isout.close();
    }

    @org.junit.Test
    public void getPossibleConversions() {
        assertNotNull(converter.getPossibleConversions());
        System.out.println(converter.getPossibleConversions());
    }
}