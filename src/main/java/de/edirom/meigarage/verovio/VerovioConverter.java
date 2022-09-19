package de.edirom.meigarage.verovio;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import pl.psnc.dl.ege.component.Converter;
import pl.psnc.dl.ege.configuration.EGEConfigurationManager;
import pl.psnc.dl.ege.configuration.EGEConstants;
import pl.psnc.dl.ege.exception.ConverterException;
import pl.psnc.dl.ege.types.ConversionActionArguments;
import pl.psnc.dl.ege.types.DataType;
import pl.psnc.dl.ege.utils.EGEIOUtils;
import pl.psnc.dl.ege.utils.IOResolver;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class VerovioConverter implements Converter {

    private static final Logger LOGGER = LogManager.getLogger(VerovioConverter.class);
    private IOResolver ior = EGEConfigurationManager.getInstance()
            .getStandardIOResolver();

    public void convert(InputStream inputStream, OutputStream outputStream, ConversionActionArguments conversionDataTypes) throws ConverterException, IOException {
        convert(inputStream, outputStream, conversionDataTypes, null);
    }

    public void convert(InputStream inputStream, OutputStream outputStream, ConversionActionArguments conversionDataTypes, String tempDir) throws ConverterException, IOException {
        boolean found = false;

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        for (ConversionActionArguments cadt : ConverterConfiguration.CONVERSIONS) {
            if (conversionDataTypes.equals(cadt)) {
                String profile = cadt.getProperties().get(ConverterConfiguration.PROFILE_KEY);
                LOGGER.info(dateFormat.format(date) + ": Converting FROM:  "
                        + conversionDataTypes.getInputType().toString()
                        + " TO "
                        + conversionDataTypes.getOutputType().toString()
                        + " WITH profile " + profile);
                convertDocument(inputStream, outputStream, cadt.getInputType(), cadt.getOutputType(),
                        cadt.getProperties(), tempDir);
                found = true;
            }
        }
        if (!found) {
            throw new ConverterException(
                    ConverterException.UNSUPPORTED_CONVERSION_TYPES);
        }

    }

    /*
     * Prepares transformation : based on MIME type.
     */
    private void convertDocument(InputStream inputStream, OutputStream outputStream,
                                 DataType fromDataType, DataType toDataType, Map<String, String> properties, String tempDir) throws IOException,
            ConverterException {

        // MEI 4.0 to PDF
        if (fromDataType.getFormat().equals(Conversion.MEI40TOPDF.getIFormatId()) &&
                toDataType.getFormat().equals(Conversion.MEI40TOPDF.getOFormatId())) {

            properties.put("verovio-extension", "svg");
            properties.put("verovio-parameter", " --mm-output --output-to svg ");
            performVerovioTransformation(inputStream, outputStream, "mei", "pdf", properties, tempDir);

        }
        // MEI 4.0 to SVG
        else if (fromDataType.getFormat().equals(Conversion.MEI40TOSVG.getIFormatId()) &&
                toDataType.getFormat().equals(Conversion.MEI40TOSVG.getOFormatId())) {

            properties.put("verovio-extension", "svg");
            properties.put("verovio-parameter", " --output-to svg ");
            performVerovioTransformation(inputStream, outputStream, "mei", "svg", properties, tempDir);

        }
        // MEI 4.0 to MIDI
        else if (fromDataType.getFormat().equals(Conversion.MEI40TOMIDI.getIFormatId()) &&
                toDataType.getFormat().equals(Conversion.MEI40TOMIDI.getOFormatId())) {

            properties.put("verovio-extension", "mid");
            properties.put("verovio-parameter", " --output-to midi ");
            performVerovioTransformation(inputStream, outputStream, "mei", "svg", properties, tempDir);

        }
    }

    private void performVerovioTransformation(InputStream inputStream, OutputStream outputStream,
                                              String inputFormat, String outputFormat,
                                              Map<String, String> properties, String tempDir) throws IOException, ConverterException {

        File inTmpDir = null;
        File outTempDir = null;
        InputStream is = null;
        try {
            inTmpDir = prepareTempDir(tempDir);
            ior.decompressStream(inputStream, inTmpDir);
            // avoid processing files ending in .bin
            File inputFile = searchForData(inTmpDir, "^.*(?<!bin)$");
            outTempDir = prepareTempDir(tempDir);
            if (inputFile != null) {
                //String newFileName = inputFile.getAbsolutePath().substring(0, inputFile.getAbsolutePath().lastIndexOf(".")) + ".ly";
                //inputFile.renameTo(new File(newFileName));
                File outputFile = new File(outTempDir + "/output." + properties.get("verovio-extension"));
                ProcessBuilder builder = new ProcessBuilder();
                String command = "verovio " + properties.get("verovio-parameter") + " --input-from "
                        + inputFormat + " --outfile " + outputFile.getAbsolutePath()
                        + " --all-pages " + inputFile.getAbsolutePath();
                LOGGER.debug(command);
                builder.command("sh", "-c", command);

                builder.directory(inTmpDir);

                builder.redirectErrorStream(true);
                Process process = builder.start();
                BufferedReader inStreamReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                while (inStreamReader.readLine() != null) {
                    LOGGER.debug(inStreamReader.readLine());
                }
                VerovioRunner runner = new VerovioRunner(process.getInputStream());
                Executors.newSingleThreadExecutor().submit(runner);
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    throw new ConverterException("Verovio process ended with exit code " + exitCode);
                }

                if (outputFormat == "pdf") {
                    //rsvg-convert -f pdf -o out.pdf *.svg

                    File outTempDir2 = prepareTempDir(tempDir);

                    builder = new ProcessBuilder();
                    builder.command("sh", "-c", "rsvg-convert -f pdf -o " + outTempDir2.getAbsolutePath() + "/out.pdf *.svg");
                    builder.directory(outTempDir);
                    process = builder.start();

                    runner = new VerovioRunner(process.getInputStream());
                    Executors.newSingleThreadExecutor().submit(runner);
                    exitCode = process.waitFor();

                    if (exitCode != 0) {
                        throw new ConverterException("rsvg-convert process ended with exit code " + exitCode);
                    }

                    outTempDir = outTempDir2;
                }

                ior.compressData(outTempDir, outputStream);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (outTempDir != null && outTempDir.exists())
                //EGEIOUtils.deleteDirectory(outTempDir)
                ;
            if (inTmpDir != null && inTmpDir.exists())
                //EGEIOUtils.deleteDirectory(inTmpDir)
                ;
        }
        try {
            is.close();
        } catch (Exception ex) {
            // do nothing
        }
    }

    private File prepareTempDir() {
        return prepareTempDir(null);
    }

    private File prepareTempDir(String tempDir) {
        File inTempDir = null;
        String uid = UUID.randomUUID().toString();
        if (tempDir != null) {
            inTempDir = new File(tempDir + File.separator + uid
                    + File.separator);
        } else {
            inTempDir = new File(EGEConstants.TEMP_PATH + File.separator + uid
                    + File.separator);
        }
        inTempDir.mkdir();
        return inTempDir;
    }

    /*
     * Search for specified by regex file
     */
    private File searchForData(File dir, String regex) {
        for (File f : dir.listFiles()) {
            if (!f.isDirectory() && Pattern.matches(regex, f.getName())) {
                return f;
            } else if (f.isDirectory()) {
                File sf = searchForData(f, regex);
                if (sf != null) {
                    return sf;
                }
            }
        }
        return null;
    }


    public List<ConversionActionArguments> getPossibleConversions() {
        return ConverterConfiguration.CONVERSIONS;
    }
}
