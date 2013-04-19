/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.sting;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.broadinstitute.sting.gatk.walkers.diffengine.DiffEngine;
import org.broadinstitute.sting.utils.Utils;
import org.broadinstitute.sting.utils.exceptions.ReviewedStingException;

import java.io.*;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: depristo
 * Date: 7/18/11
 * Time: 9:10 AM
 *
 * Utilities for manipulating the MD5 database of previous results
 */
public class MD5DB {
    public static final Logger logger = Logger.getLogger(MD5DB.class);

    /**
     * Subdirectory under the ant build directory where we store integration test md5 results
     */
    private static final int MAX_RECORDS_TO_READ = 1000000;
    private static final int MAX_RAW_DIFFS_TO_SUMMARIZE = -1;
    public static final String LOCAL_MD5_DB_DIR = "integrationtests";
    public static final String GLOBAL_MD5_DB_DIR = "/humgen/gsa-hpprojects/GATK/data/integrationtests";

    // tracking and emitting a data file of origina and new md5s
    private final File MD5MismatchesFile;
    private final PrintStream md5MismatchStream;

    public MD5DB() {
        this(new File(MD5DB.LOCAL_MD5_DB_DIR + "/md5mismatches.txt"));
    }

    public MD5DB(final File MD5MismatchesFile) {
        this.MD5MismatchesFile = MD5MismatchesFile;

        ensureMd5DbDirectory();

        logger.debug("Creating md5 mismatch db at " + MD5MismatchesFile);
        try {
            md5MismatchStream = new PrintStream(new FileOutputStream(MD5MismatchesFile));
            md5MismatchStream.printf("%s\t%s\t%s%n", "expected", "observed", "test");
        } catch ( FileNotFoundException e ) {
            throw new ReviewedStingException("Failed to open md5 mismatch file", e);
        }

    }

    public void close() {
        if ( md5MismatchStream != null ) {
            logger.debug("Closeing md5 mismatch db at " + MD5MismatchesFile);
            md5MismatchStream.close();
        }
    }

    // ----------------------------------------------------------------------
    //
    // MD5 DB stuff
    //
    // ----------------------------------------------------------------------

    /**
     * Create the MD5 file directories if necessary
     */
    private void ensureMd5DbDirectory() {
        File dir = new File(LOCAL_MD5_DB_DIR);
        if ( ! dir.exists() ) {
            System.out.printf("##### Creating MD5 db %s%n", LOCAL_MD5_DB_DIR);
            if ( ! dir.mkdir() ) {
                throw new ReviewedStingException("Infrastructure failure: failed to create md5 directory " + LOCAL_MD5_DB_DIR);
            }
        }
    }

    /**
     * Returns the path to an already existing file with the md5 contents, or valueIfNotFound
     * if no such file exists in the db.
     *
     * @param md5
     * @param valueIfNotFound
     * @return
     */
    public String getMD5FilePath(final String md5, final String valueIfNotFound) {
        // we prefer the global db to the local DB, so match it first
        for ( String dir : Arrays.asList(GLOBAL_MD5_DB_DIR, LOCAL_MD5_DB_DIR)) {
            File f = getFileForMD5(md5, dir);
            if ( f.exists() && f.canRead() )
                return f.getPath();
        }

        return valueIfNotFound;
    }

    /**
     * Utility function that given a file's md5 value and the path to the md5 db,
     * returns the canonical name of the file. For example, if md5 is XXX and db is YYY,
     * this will return YYY/XXX.integrationtest
     *
     * @param md5
     * @param dbPath
     * @return
     */
    private File getFileForMD5(final String md5, final String dbPath) {
        final String basename = String.format("%s.integrationtest", md5);
        return new File(dbPath + "/" + basename);
    }

    /**
     * Copies the results file with md5 value to its canonical file name and db places
     *
     * @param md5
     * @param resultsFile
     */
    private void updateMD5Db(final String md5, final File resultsFile) {
        copyFileToDB(getFileForMD5(md5, LOCAL_MD5_DB_DIR), resultsFile);
        copyFileToDB(getFileForMD5(md5, GLOBAL_MD5_DB_DIR), resultsFile);
    }

    /**
     * Low-level utility routine that copies resultsFile to dbFile
     * @param dbFile
     * @param resultsFile
     */
    private void copyFileToDB(File dbFile, final File resultsFile) {
        if ( ! dbFile.exists() ) {
            // the file isn't already in the db, copy it over
            System.out.printf("##### Updating MD5 file: %s%n", dbFile.getPath());
            try {
                FileUtils.copyFile(resultsFile, dbFile);
            } catch ( IOException e ) {
                System.out.printf("##### Skipping update, cannot write file %s%n", dbFile);
            }
        } else {
            //System.out.printf("##### MD5 file is up to date: %s%n", dbFile.getPath());
        }
    }

    /**
     * Returns the byte[] of the entire contents of file, for md5 calculations
     * @param file
     * @return
     * @throws IOException
     */
    private static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        // Get the size of the file
        long length = file.length();

        if (length > Integer.MAX_VALUE) {
            // File is too large
        }

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
                && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file " + file.getName());
        }

        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    public static class MD5Match {
        final String actualMD5, expectedMD5;
        final String failMessage;
        boolean failed;

        public MD5Match(final String actualMD5, final String expectedMD5, final String failMessage, final boolean failed) {
            this.actualMD5 = actualMD5;
            this.expectedMD5 = expectedMD5;
            this.failMessage = failMessage;
            this.failed = failed;
        }
    }

    /**
     * Tests a file MD5 against an expected value, returning the MD5.  NOTE: This function WILL throw an exception if the MD5s are different.
     * @param name Name of the test.
     * @param resultsFile File to MD5.
     * @param expectedMD5 Expected MD5 value.
     * @param parameterize If true or if expectedMD5 is an empty string, will print out the calculated MD5 instead of error text.
     * @return The calculated MD5.
     */
    public MD5Match assertMatchingMD5(final String name, final File resultsFile, final String expectedMD5, final boolean parameterize) {
        final String actualMD5 = testFileMD5(name, resultsFile, expectedMD5, parameterize);
        String failMessage = null;
        boolean failed = false;

        if (parameterize || expectedMD5.equals("")) {
            // Don't assert
        } else if ( actualMD5.equals(expectedMD5) ) {
            //BaseTest.log(String.format("  => %s PASSED (expected=%s)", name, expectedMD5));
        } else {
            failed = true;
            failMessage = String.format("%s has mismatching MD5s: expected=%s observed=%s", name, expectedMD5, actualMD5);
        }

        return new MD5Match(actualMD5, expectedMD5, failMessage, failed);
    }


    /**
     * Tests a file MD5 against an expected value, returning the MD5.  NOTE: This function WILL NOT throw an exception if the MD5s are different.
     * @param name Name of the test.
     * @param resultsFile File to MD5.
     * @param expectedMD5 Expected MD5 value.
     * @param parameterize If true or if expectedMD5 is an empty string, will print out the calculated MD5 instead of error text.
     * @return The calculated MD5.
     */
    public String testFileMD5(final String name, final File resultsFile, final String expectedMD5, final boolean parameterize) {
        try {
            final String filemd5sum = Utils.calcMD5(getBytesFromFile(resultsFile));

            //
            // copy md5 to integrationtests
            //
            updateMD5Db(filemd5sum, resultsFile);

            if (parameterize || expectedMD5.equals("")) {
                BaseTest.log(String.format("PARAMETERIZATION: file %s has md5 = %s", resultsFile, filemd5sum));
            } else {
                //System.out.println(String.format("Checking MD5 for %s [calculated=%s, expected=%s]", resultsFile, filemd5sum, expectedMD5));
                //System.out.flush();

                if ( ! expectedMD5.equals(filemd5sum) ) {
                    // we are going to fail for real in assertEquals (so we are counted by the testing framework).
                    // prepare ourselves for the comparison
                    System.out.printf("##### Test %s is going to fail #####%n", name);
                    String pathToExpectedMD5File = getMD5FilePath(expectedMD5, "[No DB file found]");
                    String pathToFileMD5File = getMD5FilePath(filemd5sum, "[No DB file found]");
                    BaseTest.log(String.format("expected   %s", expectedMD5));
                    BaseTest.log(String.format("calculated %s", filemd5sum));
                    BaseTest.log(String.format("diff %s %s", pathToExpectedMD5File, pathToFileMD5File));

                    md5MismatchStream.printf("%s\t%s\t%s%n", expectedMD5, filemd5sum, name);
                    md5MismatchStream.flush();

                    // inline differences
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final PrintStream ps = new PrintStream(baos);
                    DiffEngine.SummaryReportParams params = new DiffEngine.SummaryReportParams(ps, 20, 10, 0, MAX_RAW_DIFFS_TO_SUMMARIZE, false);
                    boolean success = DiffEngine.simpleDiffFiles(new File(pathToExpectedMD5File), new File(pathToFileMD5File), MAX_RECORDS_TO_READ, params);
                    if ( success ) {
                        final String content = baos.toString();
                        BaseTest.log(content);
                        System.out.printf("Note that the above list is not comprehensive.  At most 20 lines of output, and 10 specific differences will be listed.  Please use -T DiffObjects -R public/testdata/exampleFASTA.fasta -m %s -t %s to explore the differences more freely%n",
                                pathToExpectedMD5File, pathToFileMD5File);
                    }
                    ps.close();
                }
            }

            return filemd5sum;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read bytes from calls file: " + resultsFile, e);
        }
    }
}
