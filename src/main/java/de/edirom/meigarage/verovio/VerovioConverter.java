package de.edirom.meigarage.verovio;

import org.apache.log4j.Logger;
import pl.psnc.dl.ege.component.Converter;
import pl.psnc.dl.ege.configuration.EGEConfigurationManager;
import pl.psnc.dl.ege.configuration.EGEConstants;
import pl.psnc.dl.ege.exception.ConverterException;
import pl.psnc.dl.ege.types.ConversionActionArguments;
import pl.psnc.dl.ege.types.DataType;
import pl.psnc.dl.ege.utils.EGEIOUtils;
import pl.psnc.dl.ege.utils.IOResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class VerovioConverter implements Converter {

    private static final Logger LOGGER = Logger.getLogger(VerovioConverter.class);
    private IOResolver ior = EGEConfigurationManager.getInstance()
            .getStandardIOResolver();

    public void convert(InputStream inputStream, OutputStream outputStream, ConversionActionArguments conversionDataTypes) throws ConverterException, IOException {
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
                        + " WITH profile " + profile );
                convertDocument(inputStream, outputStream, cadt.getInputType(), cadt.getOutputType(),
                        cadt.getProperties());
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
                                 DataType fromDataType, DataType toDataType, Map<String, String> properties) throws IOException,
            ConverterException {

        // MEI 4.0 to PDF
        if (fromDataType.getFormat().equals(Conversion.MEI40TOPDF.getIFormatId()) &&
                toDataType.getFormat().equals(Conversion.MEI40TOPDF.getOFormatId())) {

            properties.put("verovio-extension", "svg");
            properties.put("verovio-parameter", " --mm-output --type svg ");
            performVerovioTransformation(inputStream, outputStream, "mei", "pdf", properties);

        }
        // MEI 4.0 to SVG
        else if (fromDataType.getFormat().equals(Conversion.MEI40TOSVG.getIFormatId()) &&
                toDataType.getFormat().equals(Conversion.MEI40TOSVG.getOFormatId())) {

            properties.put("verovio-extension", "svg");
            properties.put("verovio-parameter", " --type svg ");
            performVerovioTransformation(inputStream, outputStream, "mei", "svg", properties);

        }
        // MEI 4.0 to MIDI
        else if (fromDataType.getFormat().equals(Conversion.MEI40TOMIDI.getIFormatId()) &&
                toDataType.getFormat().equals(Conversion.MEI40TOMIDI.getOFormatId())) {

            properties.put("verovio-extension", "mid");
            properties.put("verovio-parameter", " --type midi ");
            performVerovioTransformation(inputStream, outputStream, "mei", "svg", properties);

        }
    }

    private void performVerovioTransformation(InputStream inputStream, OutputStream outputStream,
                                              String inputFormat, String outputFormat,
                                               Map<String, String> properties) throws IOException, ConverterException {

        File inTmpDir = null;
        File outTempDir = null;
        try {
            inTmpDir = prepareTempDir();
            ior.decompressStream(inputStream, inTmpDir);
            // avoid processing files ending in .bin
            File inputFile = searchForData(inTmpDir, "^.*(?<!bin)$");
            if(inputFile!=null) {
                //String newFileName = inputFile.getAbsolutePath().substring(0, inputFile.getAbsolutePath().lastIndexOf(".")) + ".ly";
                //inputFile.renameTo(new File(newFileName));

                outTempDir = prepareTempDir();

                ProcessBuilder builder = new ProcessBuilder();
                builder.command("sh", "-c", "verovio " + properties.get("verovio-parameter") + " --from "
                        + inputFormat + " --outfile " + outTempDir + "/output." + properties.get("verovio-extension")
                        + " --all-pages " + inputFile.getAbsolutePath());

                builder.directory(inTmpDir);
                Process process = builder.start();

                VerovioRunner runner = new VerovioRunner(process.getInputStream());
                Executors.newSingleThreadExecutor().submit(runner);
                int exitCode = process.waitFor();

                if(exitCode != 0) {
                    throw new ConverterException("Verovio process ended with exit code " + exitCode);
                }

                if(outputFormat == "pdf") {
                    //rsvg-convert -f pdf -o out.pdf *.svg

                    File outTempDir2 = prepareTempDir();

                    builder = new ProcessBuilder();
                    builder.command("sh", "-c", "rsvg-convert -f pdf -o " + outTempDir2.getAbsolutePath() + "/out.pdf *.svg");
                    builder.directory(outTempDir);
                    process = builder.start();

                    runner = new VerovioRunner(process.getInputStream());
                    Executors.newSingleThreadExecutor().submit(runner);
                    exitCode = process.waitFor();

                    if(exitCode != 0) {
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
                EGEIOUtils.deleteDirectory(outTempDir);
            if (inTmpDir != null && inTmpDir.exists())
                EGEIOUtils.deleteDirectory(inTmpDir);
        }
    }

    private File prepareTempDir() {
        File inTempDir = null;
        String uid = UUID.randomUUID().toString();
        inTempDir = new File(EGEConstants.TEMP_PATH + File.separator + uid
                + File.separator);
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
    }}
