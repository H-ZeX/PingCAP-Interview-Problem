package product;

import operator.Filter;
import operator.Split;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

public class FindTarget {
    private final long ALL_MEM = Runtime.getRuntime().maxMemory();
    private final long DEFAULT_MIDDLE_FILE_SIZE = (long) (Math.ceil(ALL_MEM * 0.75));
    private final long DEFAULT_FREE_MEM_LOW_BOUND = (long) (ALL_MEM * 0.1);
    private final int MIN_BUF_CAPACITY = 1024;
    private final long MIDDLE_FILE_SIZE;
    private final long FREE_MEM_LOW_BOUND;
    private final int BUF_CAPACITY;
    // The cnt of split round, if over this number,
    // exit and report that this program is not competent.
    private final int MAX_SPLIT_ROUND_CNT = 5;
    @SuppressWarnings("unchecked")
    private final Function<String, Integer>[] HASH_FUNC_SET =
            (Function<String, Integer>[]) new Function[MAX_SPLIT_ROUND_CNT];

    {
        // Assume that every string's avg len is 100, then its size is 200.
        int tmp = (int) (ALL_MEM * 0.05) / 200;
        BUF_CAPACITY = tmp < MIN_BUF_CAPACITY ? MIN_BUF_CAPACITY : tmp;

        HASH_FUNC_SET[0] = String::hashCode;
        HASH_FUNC_SET[1] = (x) -> {
            if (x.length() <= 1) {
                return x.length() == 0 ? 0 : (int) x.charAt(0);
            } else {
                int l1 = x.length() / 2;
                return x.substring(0, l1).hashCode()
                        + x.substring(l1).hashCode();
            }
        };
        HASH_FUNC_SET[2] = (x) -> {
            if (x.length() <= 2) {
                int hash = 0;
                for (int i = 0; i < x.length(); i++) {
                    hash += x.charAt(i);
                }
                return hash;
            } else {
                int l1 = x.length() / 3;
                return x.substring(0, l1).hashCode()
                        + x.substring(l1 + l1 * 2).hashCode()
                        + x.substring(l1 * 2).hashCode();
            }
        };
        HASH_FUNC_SET[3] = (x) -> {
            if (x.length() <= 3) {
                int hash = 0;
                for (int i = 0; i < x.length(); i++) {
                    hash += x.charAt(i);
                }
                return hash;
            } else {
                int l1 = x.length() / 4;
                return x.substring(0, l1).hashCode()
                        + x.substring(l1 + l1 * 2).hashCode()
                        + x.substring(l1 * 2, l1 * 3).hashCode()
                        + x.substring(l1 * 3).hashCode();
            }
        };
        HASH_FUNC_SET[4] = (x) -> {
            if (x.length() <= 4) {
                int hash = 0;
                for (int i = 0; i < x.length(); i++) {
                    hash += x.charAt(i);
                }
                return hash;
            } else {
                int l1 = x.length() / 5;
                return x.substring(0, l1).hashCode()
                        + x.substring(l1 + l1 * 2).hashCode()
                        + x.substring(l1 * 2, l1 * 3).hashCode()
                        + x.substring(l1 * 3, l1 * 4).hashCode()
                        + x.substring(l1 * 4).hashCode();
            }
        };
    }

    /**
     * If the two param is not legal, they will be set to default value.
     */
    public FindTarget(long middleFileSize, long freeMemLowBound) {
        if (middleFileSize <= 0 || middleFileSize >= ALL_MEM) {
            this.MIDDLE_FILE_SIZE = DEFAULT_MIDDLE_FILE_SIZE;
        } else {
            this.MIDDLE_FILE_SIZE = middleFileSize;
        }
        if (freeMemLowBound <= 0 || freeMemLowBound >= ALL_MEM) {
            this.FREE_MEM_LOW_BOUND = DEFAULT_FREE_MEM_LOW_BOUND;
        } else {
            this.FREE_MEM_LOW_BOUND = freeMemLowBound;
        }
        Logger.getGlobal().info("middleFileSize: " + this.MIDDLE_FILE_SIZE
                + ", freeMemLowBound: " + this.FREE_MEM_LOW_BOUND);
    }

    public String work(String splitBaseName, String inputFile,
                       String middleDir) throws Exception {
        checkParam(inputFile, middleDir);
        int partCnt = (int) (Math.ceil(new File(inputFile).length() * 1.0 / MIDDLE_FILE_SIZE) + 1);
        Logger.getGlobal().info("first split, split to " + partCnt + " parts");
        split(splitBaseName, inputFile,
                middleDir, partCnt,
                HASH_FUNC_SET[0],
                (line, number) -> new Split.LineParseResult(true, line, number)
        );
        recurseSplit(splitBaseName, middleDir);
        return filterRes(middleDir);
    }

    private void checkParam(String inputFile, String middleDir) {
        File input = new File(inputFile);
        File middle = new File(middleDir);
        if (!input.exists() || input.isDirectory()) {
            throw new IllegalArgumentException(inputFile + " should exist and should not be directory");
        }
        File[] files = middle.listFiles();
        if (!middle.isDirectory() || files.length > 0) {
            throw new IllegalArgumentException(middleDir + " should be dir, and should be empty");
        }
    }

    private void recurseSplit(String splitBaseName,
                              String middleDir)
            throws Exception {
        // Be careful that, the new file gene by split will in the same dir,
        // so it MUST not has same name as others.
        // So the split name should be construct carefully.
        boolean ok;
        int roundCnt = 0;
        do {
            ok = true;
            roundCnt++;
            if (roundCnt >= MAX_SPLIT_ROUND_CNT) {
                throw new Exception("This program is not competent for this job.");
            }
            File[] files = new File(middleDir).listFiles();
            assert files != null;
            for (int i = 0; i < files.length; i++) {
                File x = files[i];
                if (x.length() <= MIDDLE_FILE_SIZE) {
                    continue;
                }
                Logger.getGlobal().info("recurse split: " + roundCnt + ", fileSize: " + x.length()
                        + ", fileName: " + x.getAbsolutePath()
                        + ", file_size_to_filter: " + MIDDLE_FILE_SIZE);
                ok = false;
                int partCnt = (int) Math.ceil(x.length() * 1.0 / MIDDLE_FILE_SIZE);
                split(splitBaseName + "_" + roundCnt + "_" + i,
                        x.getAbsolutePath(),
                        middleDir, partCnt,
                        HASH_FUNC_SET[roundCnt],
                        (line, num) -> {
                            int ind = line.lastIndexOf("\t");
                            int ind2 = line.lastIndexOf("\t", ind - 1);
                            String str = line.substring(0, ind2);
                            long n = Long.parseLong(line.substring(ind2, ind).trim());
                            int cnt = Integer.parseInt(line.substring(ind).trim());
                            return new Split.LineParseResult(cnt == 1, str, n);
                        }
                );
                Logger.getGlobal().info("delete " + x);
                if (!x.delete()) {
                    throw new Exception("delete middle file(" + x.getAbsolutePath() + ") failed");
                }
            }
        } while (!ok);
    }

    private void split(String splitName,
                       String inputFileName,
                       String outputDir,
                       int partCnt,
                       Function<String, Integer> hashFunc,
                       BiFunction<String, Long, Split.LineParseResult> lineParser)
            throws Exception {
        String[] outputDirs = new String[partCnt];
        for (int i = 0; i < partCnt; i++) {
            outputDirs[i] = outputDir;
        }
        final Split split = new Split(splitName, inputFileName, outputDirs,
                hashFunc, lineParser, FREE_MEM_LOW_BOUND, BUF_CAPACITY);
        split.start();
    }

    private String filterRes(String outputDir)
            throws IOException, InterruptedException {
        final ArrayList<Filter.Result> res = new ArrayList<>();
        final File[] files = new File(outputDir).listFiles();
        assert files != null;
        for (int i = 0; i < files.length; i++) {
            File p = files[i];
            res.add(Filter.filter(p.getAbsolutePath(), BUF_CAPACITY));
            Logger.getGlobal().info("End " + i + "'s filter");
        }
        String rs = null;
        long rn = Long.MAX_VALUE;
        for (Filter.Result x : res) {
            if (x.valid && rn > x.number) {
                rs = x.str;
                rn = x.number;
            }
        }
        return (rs == null ? "Not found" : rs);
    }
}