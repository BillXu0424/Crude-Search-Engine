package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class PersistentScalableHashedIndex extends PersistentHashedIndex {
    // public static final int THRESHOLD = 450000; /* for guardian */
    public static final int THRESHOLD = 150000; /* for davidswiki presentation */
    // public static final int THRESHOLD = 100000; /* for guardian presentation */

    int currentBatch;
    volatile int curPartialFileNums;
    TestMultiThreading mergeThread;

    public PersistentScalableHashedIndex() {
        super();
        mergeThread = new TestMultiThreading();
        currentBatch = 0;
        curPartialFileNums = 0;
        mergeThread.start();
    }
    
    class TestMultiThreading extends Thread {
        private Thread t;
        int currentMergingBatch = 1;

        class PQNode {
            String token;
            int comeFrom;

            PQNode(String token, int comeFrom) {
                this.token = token;
                this.comeFrom = comeFrom;
            }       
        }

        private class StringHashComparator implements Comparator<String> {
            @Override
            public int compare(String s1, String s2) {
                if (hashFunc(s1) != hashFunc(s2)) {
                    return Long.compare(hashFunc(s1), hashFunc(s2));
                }
                else {
                    return s1.compareTo(s2);
                }
            }
        }

        private class PQNodeComparator implements Comparator<PQNode> {
            @Override
            public int compare(PQNode n1, PQNode n2) {
                if (hashFunc(n1.token) != hashFunc(n2.token)) {
                    return Long.compare(hashFunc(n1.token), hashFunc(n2.token));
                }
                else {
                    return n1.token.compareTo(n2.token);
                }
            }

        }

        TestMultiThreading() {
            System.out.println("Merging thread created!");
        }

        public void run() {
            System.out.println("Merging thread running!");

            while (true) {
                if (curPartialFileNums > 0) {
                    System.out.println("Start merging batch " + currentMergingBatch + "...");
                    writeTokenList();
                    externalSort();
                    try {
                        mergeIndex();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Successfully merged batch " + currentMergingBatch + "!");
                    currentMergingBatch++;
                    curPartialFileNums--;
                }
            }
        }

        public void start() {
            System.out.println("Start merging thread.");
            if (t == null) {
                t = new Thread(this);
                t.start ();
             }
        }

        void mergeIndex() throws IOException {
            Scanner in = null;
            try {
                in = new Scanner(new File(INDEXDIR + "/temp/sorted_token"), StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            int count = 0;
            
            Set<Long> checkCollision = new HashSet<Long>();
            long ptr = 0;

            while (in.hasNextLine()) {
                count++;

                String token = in.nextLine();

                // System.out.println(token);

                PostingsList pl1 = getPostingsFromPartial(token, 0);
                PostingsList pl2 = getPostingsFromPartial(token, currentMergingBatch);
                long hash = hashFunc(token);
                while (checkCollision.contains(hash)) {
                    hash = (hash + 1) % TABLESIZE;
                }
                checkCollision.add(hash);
                
                if (pl1 != null && pl2 == null) {
                    String postingsListRep = pl1.toString(); 
                    int listSize = writeDataToNew(token + "\t" + postingsListRep, ptr);
                    writeEntryToNew(new Entry(listSize, ptr), hash);
                    ptr += listSize;
                    continue;
                }
                if (pl1 == null && pl2 != null) {
                    String postingsListRep = pl2.toString(); 
                    int listSize = writeDataToNew(token + "\t" + postingsListRep, ptr);
                    writeEntryToNew(new Entry(listSize, ptr), hash);
                    ptr += listSize;
                    continue;
                }
                if (pl1 != null && pl2 != null) {
                    /* merge two postings */
                    PostingsList mergedPl = new PostingsList(pl1);
                    if (pl1.get(pl1.size() - 1).docID < pl2.get(0).docID) {
                        for (int i = 0; i < pl2.size(); i++) {
                            mergedPl.appendEntry(new PostingsEntry(pl2.get(i)));
                        }
                    }
                    else { // ==
                        for (int i = 0; i < pl2.size(); i++) {
                            if (i == 0) {
                                PostingsEntry lastPE = mergedPl.get(mergedPl.size() - 1);
                                lastPE.offset.addAll(pl2.get(0).offset); 
                                continue;
                            }
                            mergedPl.appendEntry(new PostingsEntry(pl2.get(i)));
                        }
                    }
                    String postingsListRep = mergedPl.toString(); 
                    int listSize = writeDataToNew(token + "\t" + postingsListRep, ptr);
                    writeEntryToNew(new Entry(listSize, ptr), hash);
                    ptr += listSize;
                    continue;
                }
            }
            in.close();

            System.out.println("read " + count + " tokens from sorted tokens");

            /* move and replace the original main index */
            Path sourceDict = Paths.get(INDEXDIR + "/temp/" + DICTIONARY_FNAME);
            Path targetDict = Paths.get(INDEXDIR + "/" + DICTIONARY_FNAME);
            Path sourceData = Paths.get(INDEXDIR + "/temp/" + DATA_FNAME);
            Path targetData = Paths.get(INDEXDIR + "/" + DATA_FNAME);
            while (true) {
                try {
                    Files.move(sourceDict, targetDict, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Dictionary file moved successfully");
                    break;
                } catch (IOException e) {
                    // System.out.println("Failed to move the dictionary file.");
                    // e.printStackTrace();
                }
            }
            while (true) {
                try {
                    Files.move(sourceData, targetData, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Data file moved successfully");
                    break;
                } catch (IOException e) {
                    // System.out.println("Failed to move the dictionary Data.");
                    // e.printStackTrace();
                }
            }
            
            /* merge the docinfo File */
            while (true) {
                try {
                    List<String> lines = Files.readAllLines(Paths.get(INDEXDIR + "/" + DOCINFO_FNAME + currentMergingBatch));
                    Files.write(Paths.get(INDEXDIR + "/" + DOCINFO_FNAME), lines, StandardOpenOption.APPEND);
                    System.out.println("Docinfo file merged successfully.");
                    break;
                } catch (IOException e) {
                    // System.out.println("Failed to merge the docinfo file.");
                    // e.printStackTrace();
                }
            }       
            deleteFile(INDEXDIR + "/temp/sorted_token");
        }

        void externalSort() {
            int k = divideFiles();
            mergeFiles(k);
            // deleteFile(INDEXDIR + "/temp/raw_tokens");
            for (int i = 0; i < k; i++) {
                deleteFile(INDEXDIR + "/temp/token_block" + i);
            }
            System.out.println("Successully sort tokens!");
        }

        int divideFiles() {
            // Read the file for many times (each part THRESHOLD tokens, except the last file), sort each of them, and finally write them to the disk.
            // Return number of parts k.

            Scanner in = null;
            try {
                in = new Scanner(new File(INDEXDIR + "/temp/raw_tokens"), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            long numLines = 0;
            try {
                numLines = Files.lines(Paths.get(INDEXDIR + "/temp/raw_tokens")).parallel().count();

            } catch (IOException e) {
                e.printStackTrace();
            }

            int k = (int) -Math.floorDiv(-numLines, THRESHOLD); // equals to ceildiv
            System.out.println("Divide sorted tokens into " + k + " token files...");

            // output temporary files
            PrintWriter[] out = new PrintWriter[k];
            for (int i = 0; i < k; i++) {
                try {
                    out[i] = new PrintWriter(INDEXDIR + "/temp/token_block" + String.valueOf(i), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            ArrayList<String> arr = new ArrayList<>();

            boolean more_input = true;
            int next_output_file = 0;

            int i;
            while (more_input) { 
                if (!in.hasNextLine()) break;

                for (i = 0; i < THRESHOLD; i++) {
                    if (!in.hasNextLine()) {
                        more_input = false;
                        break;
                    }
                    arr.add(in.nextLine());
                }

                System.out.println("originally " + arr.size() + " tokens");

                // remove duplicates and sort array
                Set<String> unique = new HashSet<>(arr);
                arr.clear();
                arr.addAll(unique);
                Collections.sort(arr, new StringHashComparator());

                System.out.println(arr.size() + " tokens after removing duplicates");

                for (int j = 0; j < arr.size(); j++)
                    out[next_output_file].println(arr.get(j));
                arr.clear();

                next_output_file++;
            }

            // close input and output files
            for (i = 0; i < k; i++)
                out[i].close();

            in.close();
            return k;
        }

        void mergeFiles(int k) {
            System.out.println("Merging " + k + " token files...");

            PriorityQueue<PQNode> queue = new PriorityQueue<>(new PQNodeComparator());
            String token;

            BufferedReader[] in = new BufferedReader[k];
            for (int i = 0; i < k; i++) {
                try {
                    in[i] = new BufferedReader(new FileReader(INDEXDIR + "/temp/token_block" + i, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            BufferedWriter out = null;
            try {
                out = new BufferedWriter(new FileWriter(INDEXDIR + "/temp/sorted_token", StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < k; i++) {
                try {
                    token = in[i].readLine();
                    if (token == null) break;
                    queue.add(new PQNode(token, i));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            int count = 0;
            String previousToken = null;

            while (count != k) {
                PQNode minElem = queue.poll();
                String minToken = minElem.token;
                int minComeFrom = minElem.comeFrom;
                try {
                    String newToken = in[minComeFrom].readLine();
                    if (newToken == null) {
                        count++;
                    } else {
                        queue.add(new PQNode(newToken, minComeFrom));
                    }
                    if (previousToken != null && previousToken.equals(minToken)) continue;
                    previousToken = minToken;
                    out.write(minToken);
                    out.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        
            for (int j = 0; j < k; j++) {
                try {
                    in[j].close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }            
        }

        void writeTokenList() {
            String token;

            System.out.println("Writing raw token lists...");

            if (currentMergingBatch == 1) {
                try {
                    Path sourceFile = Paths.get(INDEXDIR + "/temp/raw0");
                    Path destinationFile = Paths.get(INDEXDIR + "/temp/raw_tokens");
                    Files.copy(sourceFile, destinationFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                File file1 = new File(INDEXDIR + "/temp/raw" + currentMergingBatch);
                File file2 = new File(INDEXDIR + "/temp/raw_tokens");
                FileWriter fw = new FileWriter(file2, StandardCharsets.UTF_8, true);
                BufferedWriter bw = new BufferedWriter(fw);
                FileReader fr = new FileReader(file1, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(fr);
    
                while ((token = br.readLine()) != null) {
                    bw.write(token);
                    bw.newLine();
                }
    
                br.close();
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Entry readEntryFromPartial(long ptr, String filename) throws IOException {   
            RandomAccessFile partialDictionary = new RandomAccessFile(INDEXDIR + "/" + filename, "rw");
            try {
                partialDictionary.seek(ptr * Entry.size);
                long listPtr = partialDictionary.readLong();
                int listSize = partialDictionary.readInt();
                Entry entry = new Entry(listSize, listPtr);
                partialDictionary.close();
                return entry;
            } catch (IOException e) {
                partialDictionary.close();
                // e.printStackTrace();
            }
            return null;
        }
    
        String readDataFromPartial(long ptr, int size, String filename) throws IOException {
            RandomAccessFile partialData = new RandomAccessFile(INDEXDIR + "/" + filename, "rw");
            try {
                partialData.seek(ptr);
                byte[] data = new byte[size];
                partialData.readFully(data);
                partialData.close();
                return new String(data);
            } catch ( IOException e ) {
                partialData.close();
                // e.printStackTrace();
                return null;
            }
        }

        void writeEntryToNew(Entry entry, long ptr) {
            try (RandomAccessFile partialDictionary = new RandomAccessFile(INDEXDIR + "/temp/" + DICTIONARY_FNAME, "rw")) {
                try {
                    partialDictionary.seek(ptr * Entry.size);
                    partialDictionary.writeLong(entry.getListPtr());
                    partialDictionary.writeInt(entry.getListSize());
                    partialDictionary.close();
                } catch (IOException e) {
                    partialDictionary.close();
                    e.printStackTrace();
                }
                partialDictionary.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int writeDataToNew( String dataString, long ptr ) {
            try (RandomAccessFile partialData = new RandomAccessFile(INDEXDIR + "/temp/" + DATA_FNAME, "rw")) {
                try {
                    partialData.seek( ptr ); 
                    byte[] data = dataString.getBytes();
                    partialData.write( data );
                    partialData.close();
                    return data.length;
                } catch ( IOException e ) {
                    e.printStackTrace();
                    partialData.close();
                    return -1;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }

        PostingsList getPostingsFromPartial(String token, int batch) throws IOException {
            long hash = hashFunc(token);
            String dictFilename, dataFilename;
            if (batch == 0) {
                dictFilename = DICTIONARY_FNAME;
                dataFilename = DATA_FNAME;
            }
            else {
                dictFilename = DICTIONARY_FNAME + batch;
                dataFilename = DATA_FNAME + batch;
            }

            Entry entry = readEntryFromPartial(hash, dictFilename);
            // System.out.println(token);


            if (entry == null || (entry.getListPtr() == 0 && entry.getListSize() == 0)) {
                return null;
            } 
       
            long listPtr = entry.getListPtr();
            int listSize = entry.getListSize();
            String fetchedData = readDataFromPartial(listPtr, listSize, dataFilename);
            String[] dataSplit = fetchedData.split("\t");
            while (!dataSplit[0].equals(token)) {
                hash = (hash + 1) % TABLESIZE;
                entry = readEntryFromPartial(hash, dictFilename);
    
                if (entry == null || (entry.getListPtr() == 0 && entry.getListSize() == 0)) {
                    return null;
                } 
    
                listPtr = entry.getListPtr();
                listSize = entry.getListSize();
                fetchedData = readDataFromPartial(listPtr, listSize, dataFilename);
                dataSplit = fetchedData.split("\t");
            }
            return new PostingsList(dataSplit[1]);
        }

        boolean deleteFile(String fileName) {
            File file = new File(fileName);
            if (file.exists() && file.isFile()) {
                System.out.println("Delete successfully!");
                return file.delete();
            }
            System.out.println("Fail to delete this file!");
            return false;
        }

        boolean deletePartialFiles(int mergingBatch) {
            boolean deletedDict = deleteFile(INDEXDIR + "/" + DICTIONARY_FNAME + mergingBatch);
            System.out.println(deletedDict);
            boolean deletedData = deleteFile(INDEXDIR + "/" + DATA_FNAME + mergingBatch);
            System.out.println(deletedData);
            boolean deletedDoc = deleteFile(INDEXDIR + "/" + DOCINFO_FNAME + mergingBatch);
            System.out.println(deletedDoc);
            return deletedDict && deletedData && deletedDoc;
        }
    
    }

    /**
     *  Inserts this token in the main-memory hashtable.
     */
    @Override
    public void insert( String token, int docID, int offset ) {
        PostingsEntry newEntry = new PostingsEntry(docID);

        if (index.get(token) == null) {
            PostingsList newList = new PostingsList();
            newList.addPostingsEntry(newEntry, offset);
            index.put(token, newList);
        }
        else {
            PostingsList existedList = index.get(token);
            existedList.addPostingsEntry(newEntry, offset);
        }
                
        if (index.keySet().size() == THRESHOLD) {
            writeIndex();
            index.clear();
            docNames.clear();
            docLengths.clear();
            free = 0L;
            currentBatch++;
            try {
                dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + currentBatch, "rw");
                dataFile = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + currentBatch, "rw");
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
        /* If length of index reaches specific point, write index (data, dictionary, docInfo) to disk. Done
         * Clear index, docNames, and docLengths. Done
         * Set pointer free to 0. Done
         * Recreate RAF dictionaryFile and dataFile. Done
         * If more than 2 disk indices has been written, start merging thread (just linear merging: everytime, merge index_x and index_1 into index_1). Done
         * (merge algorithm should use external sorting, i.e., based on disk, because we may not have so much main mem.)
         */
    }


    class DocNamesEntry {
        int key;
        String name;
        DocNamesEntry(int key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    class DocNamesEntryComparator implements Comparator<DocNamesEntry> {
        @Override
        public int compare(DocNamesEntry e1, DocNamesEntry e2) {
            return Integer.compare(e1.key, e2.key);
        }
    }


    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        String filename = new String();
        if (currentBatch == 0) {
            filename = INDEXDIR + "/docInfo";
        }
        else {
            filename = INDEXDIR + "/docInfo" + currentBatch;
        }
        FileOutputStream fout = new FileOutputStream(filename);

        ArrayList<DocNamesEntry> docNamesEntries = new ArrayList<>(); 
        for ( Map.Entry<Integer,String> entry : docNames.entrySet() ) {
            DocNamesEntry docNamesEntry = new DocNamesEntry(entry.getKey(), entry.getValue());
            docNamesEntries.add(docNamesEntry);
        }
        Collections.sort(docNamesEntries, new DocNamesEntryComparator());

        for (DocNamesEntry entry : docNamesEntries) {
            String docInfoEntry = entry.key + ";" + entry.name + ";" + docLengths.get(entry.key) + "\n";
            fout.write( docInfoEntry.getBytes() );
        }
        fout.close();
    }

     /**
     *  Write the index to files.
     */
    @Override
    public void writeIndex() {
        int collisions = 0;
        Set<Long> checkCollision = new HashSet<Long>();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(INDEXDIR + "/temp/raw" + currentBatch, StandardCharsets.UTF_8))) {
            System.out.println("Writing batch " + currentBatch + "...");

            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();

            // Write the dictionary and the postings list
            for (String term : index.keySet()) {
                bw.write(term);
                bw.newLine();

                long hash = hashFunc(term);
                if (checkCollision.contains(hash)) collisions++;
                while (checkCollision.contains(hash)) {
                    hash = (hash + 1) % TABLESIZE;
                }
                checkCollision.add(hash);
                PostingsList postingsList = index.get(term);
                String postingsListRep = postingsList.toString();
                int listSize = writeData(term + "\t" + postingsListRep, free);
                writeEntry(new Entry(listSize, free), hash);
                free += listSize;
            }
            bw.flush();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
        System.out.println("Successfully written batch " + currentBatch + "!");
        if (currentBatch >= 1) curPartialFileNums++;
        System.out.println(curPartialFileNums);
        
    }

    /**
     *  Write index to file after indexing is done.
     */
    @Override
    public void cleanup() {
        writeIndex();

        while (curPartialFileNums > 0)
        
        mergeThread.interrupt();

        File directory = new File(INDEXDIR + "/temp");

        for (File file : directory.listFiles()) {
            file.delete();
        }

        try {
            dictionaryFile.close();
            dataFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        directory = new File(INDEXDIR);
        for (File file : directory.listFiles()) {
            while (file.exists() && file.getName().matches(".*\\d+")) {
                file.delete();
            }
        }
        System.out.println( "done!" );
    }
}