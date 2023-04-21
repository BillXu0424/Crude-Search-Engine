package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/**
 *   Processes a directory structure and indexes all PDF and text files.
 */
public class Indexer {

    /** The index to be built up by this Indexer. */
    Index index;

    /** K-gram index to be built up by this Indexer */
    KGramIndex kgIndex;

    /** The next docID to be generated. */
    private int lastDocID = 0;

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file;


    /* ----------------------------------------------- */


    /** Constructor */
    public Indexer( Index index, KGramIndex kgIndex, String patterns_file ) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.patterns_file = patterns_file;
    }


    /** Generates a new document identifier as an integer. */
    private int generateDocID() {
        return lastDocID++;
    }



    /**
     *  Tokenizes and indexes the file @code{f}. If <code>f</code> is a directory,
     *  all its files and subdirectories are recursively processed.
     */
    public void processFiles( File f, boolean is_indexing, boolean writeL2 ) {
        // do not try to index fs that cannot be read
        if (is_indexing) {
            if ( f.canRead() ) {
                if ( f.isDirectory() ) {
                    String[] fs = f.list();
                    // an IO error could occur
                    if ( fs != null ) {
                        for ( int i=0; i<fs.length; i++ ) {
                            processFiles( new File( f, fs[i] ), is_indexing, writeL2 );
                        }
                    }
                } else {
                    // HashMap<String, Integer> wordCount = new HashMap<>();
                    // First register the document and get a docID
                    int docID = generateDocID();
                    if ( docID%1000 == 0 ) System.err.println( "Indexed " + docID + " files" );
                    try {
                        Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
                        Tokenizer tok = new Tokenizer( reader, true, false, true, patterns_file );
                        int offset = 0;
                        HashSet<String> uniqueTokens = new HashSet<>();
                        while ( tok.hasMoreTokens() ) {
                            String token = tok.nextToken();
                            insertIntoIndex( docID, token, offset++ );
                            /* save to wordCount, used for euclidean */
                            if (writeL2) {
                                if (index.tf.containsKey(docID)) {
                                    if (index.tf.get(docID).containsKey(token)) {
                                        index.tf.get(docID).put(token, index.tf.get(docID).get(token) + 1);
                                    }
                                    else {
                                        index.tf.get(docID).put(token, 1);
                                    }
                                }
                                else {
                                    index.tf.put(docID, new HashMap<String, Integer>());
                                    index.tf.get(docID).put(token, 1);
                                }
                                uniqueTokens.add(token);
                            }
                        }
                        if (writeL2) {
                            for (String uniqueToken : uniqueTokens) {
                                if (index.df.containsKey(uniqueToken)) {
                                    index.df.put(uniqueToken, index.df.get(uniqueToken) + 1);
                                }
                                else {
                                    index.df.put(uniqueToken, 1);
                                }
                            }
                        }
                        index.docNames.put( docID, f.getPath() );
                        index.docIdentifiers.put(getFileName(f.getPath()), docID);
                        index.docLengths.put( docID, offset );
                        reader.close();
                    }
                    catch ( IOException e ) {
                        System.err.println( "Warning: IOException during indexing." );
                    }
                }
            }
        }
    }

    /** Read pageranks and the document names. Store them into docRanks. */
    void readPageRank(String filename) {
        BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(filename));
			String line = reader.readLine();

			while (line != null) {
				index.docRanks.put(line.split(" ")[1], Double.valueOf(line.split(" ")[0]));
				// read next line
				line = reader.readLine();
			}
            System.out.println("Read pagerank successfully from " + filename);
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    void readEuclideanLengths(String filename) {
        BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(filename));
			String line = reader.readLine();

			while (line != null) {
				index.l2Lengths.put(Integer.parseInt(line.split(" ")[1]), Double.valueOf(line.split(" ")[0]));
				line = reader.readLine();
			}
            System.out.println("Read Euclidean lengths successfully from " + filename);
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }


    /* ----------------------------------------------- */


    /**
     *  Indexes one token.
     */
    public void insertIntoIndex( int docID, String token, int offset ) {
        index.insert( token, docID, offset );
        if (kgIndex != null)
            kgIndex.insert(token);
    }

        /**
     * A utility function that gets a file name given its path.
     * For example, given the path "davisWiki/hello.f",
     * the function will return "hello.f".
     *
     * @param      path  The file path
     *
     * @return     The file name.
     */
    private String getFileName( String path ) {
        String result = "";
        StringTokenizer tok = new StringTokenizer( path, "\\\\" );
        while ( tok.hasMoreTokens() ) {
            result = tok.nextToken();
        }
        return result;
    }
}

