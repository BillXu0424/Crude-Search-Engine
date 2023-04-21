package ir;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

/**
 *  This is the main class for the search engine.
 */
public class Engine {

    /** The inverted index. */
    Index index = new HashedIndex();
    // Index index = new PersistentHashedIndex();
    // Index index = new PersistentScalableHashedIndex();

    /** The indexer creating the search index. */
    Indexer indexer;

    /** K-gram index */
    KGramIndex kgIndex = new KGramIndex(2);

    /** The searcher used to search the index. */
    Searcher searcher;

    /** Spell checker */
    SpellChecker speller;

    /** The engine GUI. */
    SearchGUI gui;

    /** Directories that should be indexed. */
    ArrayList<String> dirNames = new ArrayList<String>();

    /** Lock to prevent simultaneous access to the index. */
    Object indexLock = new Object();

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file = null;

    /** The file containing the logo. */
    String pic_file = "";

    /** The file containing the pageranks. */
    String rank_file = "";

    /** For persistent indexes, we might not need to do any indexing. */
    boolean is_indexing = true;


    /* ----------------------------------------------- */


    /**  
     *   Constructor. 
     *   Indexes all chosen directories and files
     */
    public Engine( String[] args ) {
        decodeArgs( args );
        indexer = new Indexer( index, kgIndex, patterns_file );
        searcher = new Searcher( index, kgIndex );
        speller = new SpellChecker(index, kgIndex);
        gui = new SearchGUI( this );
        gui.init();
        /* 
         *   Calls the indexer to index the chosen directory structure.
         *   Access to the index is synchronized since we don't want to 
         *   search at the same time we're indexing new files (this might 
         *   corrupt the index).
         */

        indexer.readPageRank("../pagerank/davisRank.txt");

        if (is_indexing) {
            synchronized ( indexLock ) {
                gui.displayInfoText( "Indexing, please wait..." );
                long startTime = System.currentTimeMillis();

                boolean writeL2 = true;
                File l2LengthFile = new File("./euclidean_length.txt");
                if (l2LengthFile.isFile()) { 
                    writeL2 = false;
                    indexer.readEuclideanLengths("./euclidean_length.txt");
                }

                for ( int i=0; i<dirNames.size(); i++ ) {
                    File dokDir = new File( dirNames.get( i ));
                    indexer.processFiles(dokDir, is_indexing, writeL2);
                }

                // System.out.println("ve: " + indexer.kgIndex.getPostings("ve").size());
                // List<KGramPostingsEntry> pe = indexer.kgIndex.getPostings("th");
                // pe = indexer.kgIndex.intersect(pe, indexer.kgIndex.getPostings("he"));
                // System.out.println("th he: " + pe.size());

                if (writeL2) {
                    saveL2Length();
                }
                long elapsedTime = System.currentTimeMillis() - startTime;
                gui.displayInfoText( String.format( "Indexing done in %.1f seconds.", elapsedTime/1000.0 ));
                index.cleanup();
            }
        } else {
            gui.displayInfoText( "Index is loaded from disk" );
        }
    }

    /**
     * Save Euclidean length to disk.
     */
    private void saveL2Length() {
        double eucLength = 0;
        try {
            File file = new File("./euclidean_length.txt");
        
            // Check if the file exists
            if (!file.exists()) {
                file.createNewFile();
            }
        
            // Open the file in append mode
            FileWriter fileWriter = new FileWriter(file, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            for (int docID : index.tf.keySet()) {
                for (String token : index.tf.get(docID).keySet()) {
                    eucLength += Math.pow(index.tf.get(docID).get(token) * Math.log((double)index.docNames.size() / (double)index.df.get(token)), 2);
                }
                eucLength = Math.sqrt(eucLength);
                // Append the text to the file
                bufferedWriter.write(Double.toString(eucLength) + " " + docID);
                bufferedWriter.newLine();
                eucLength = 0;
            }

            // Close the writers
            bufferedWriter.close();
            fileWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /* ----------------------------------------------- */

    /**
     *   Decodes the command line arguments.
     */
    private void decodeArgs( String[] args ) {
        int i=0, j=0;
        while ( i < args.length ) {
            if ( "-d".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    dirNames.add( args[i++] );
                }
            } else if ( "-p".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    patterns_file = args[i++];
                }
            } else if ( "-l".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    pic_file = args[i++];
                }
            } else if ( "-r".equals( args[i] )) {
                i++;
                if ( i < args.length ) {
                    rank_file = args[i++];
                }
            } else if ( "-ni".equals( args[i] )) {
                i++;
                is_indexing = false;
            } else {
                System.err.println( "Unknown option: " + args[i] );
                break;
            }
        }                   
    }


    /* ----------------------------------------------- */


    public static void main( String[] args ) {
        Engine e = new Engine( args );
    }

}

