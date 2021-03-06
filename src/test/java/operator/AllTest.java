package operator;

import org.junit.Test;

import java.io.*;
import java.util.*;

public class AllTest {
    @Test
    public void test1() throws Exception {
        final int maxWordCnt = 10240;
        final int maxWordSize = 100;
        final int minWordSize = 1;
        final int testCnt = 1;
        final String outputDir = "/tmp/split_tests____/";
        final String material = "/tmp/render_file";
        final String splitName = "split_1";
        final Random rng = new Random();

        for (int i = 0; i < testCnt; i++) {
            File output = new File(outputDir);
            if (!output.exists()) {
                output.mkdirs();
            } else {
                if (!output.isDirectory()) {
                    output.delete();
                    output.mkdirs();
                } else {
                    File[] files = output.listFiles();
                    for (File x : files) {
                        x.delete();
                    }
                }
            }
            String result = TesterUtil.renderWord(rng.nextInt(maxWordSize - minWordSize) + minWordSize, rng);
            renderFile(rng.nextInt(maxWordCnt), maxWordSize, minWordSize, rng, material, result);
            Split split = new Split(splitName, material,
                    new String[]{outputDir, outputDir, outputDir, outputDir, outputDir, outputDir, outputDir},
                    String::hashCode, (line, num) -> new Split.LineParseResult(true, line, num),
                    128L * 1024 * 1024);
            split.start();

            Process checker = Runtime.getRuntime().exec("/home/hzx/IdeaProjects/PingCapHW2/src/test/java/checker.sh "
                    + outputDir + " " + material);
            redirect(checker.getErrorStream(), System.err);
            int exitCode = checker.waitFor();
            assert exitCode == 0 : exitCode;

            File outDir = new File(outputDir);
            File[] files = outDir.listFiles();
            assert files != null;
            ArrayList<Filter.Result> res = new ArrayList<>();
            for (File x : files) {
                res.add(Filter.filter(x.getAbsolutePath()));
            }
            String rs = null;
            long rn = Long.MAX_VALUE;
            for (Filter.Result x : res) {
                System.out.println(x);
                if (x.valid && rn > x.number) {
                    rs = x.str;
                    rn = x.number;
                }
            }
            assert result.equals(rs) : "\n" + rs + "\n" + result;
        }
    }

    private static void redirect(InputStream in, OutputStream out) throws IOException {
        while (true) {
            int c = in.read();
            if (c == -1) break;
            out.write((char) c);
        }
    }

    private void renderFile(int wordCnt, int maxWorSize,
                            int minWordSize, Random rng,
                            String fileName, String result)
            throws IOException {
        FileOutputStream output = new FileOutputStream(fileName);
        boolean hasRes = false;
        for (int i = 0; i < wordCnt; i++) {
            int size = rng.nextInt(maxWorSize - minWordSize) + minWordSize;
            String s;
            do {
                s = TesterUtil.renderWord(size, rng);
            } while (s.equals(result));
            output.write((s + "\n").getBytes());
            for (int j = 0; j < rng.nextInt(10) + 2; j++) {
                output.write((s + "\n").getBytes());
            }
            if (!hasRes && rng.nextInt(10) == 1) {
                output.write((result + "\n").getBytes());
                hasRes = true;
            }
        }
        if (!hasRes) {
            output.write((result + "\n").getBytes());
        }
        output.close();
    }
}