package ir;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 *  Defines some common data structures and methods that all types of
 *  index should implement.
 */
public interface Index {

    /** Mapping from document identifiers to document names. */
    public HashMap<Integer, String> docNames = new HashMap<Integer, String>();

    /** Mapping from document names to document identifiers. */
    public HashMap<String, Integer> docIdentifiers = new HashMap<String, Integer>();

    /** Mapping from document names to document pageranks */
    public HashMap<String, Double> docRanks = new HashMap<String, Double>();

    /** Mapping from document identifier to document length. */
    public HashMap<Integer, Integer> docLengths = new HashMap<Integer, Integer>();

    /** tf_{dt} */
    public HashMap<Integer, HashMap<String, Integer>> tf = new HashMap<Integer, HashMap<String, Integer>>();

    /** df_t */
    public HashMap<String, Integer> df = new HashMap<String, Integer>();

    /** Mapping from document identifier to Euclidean length. */
    public HashMap<Integer, Double> l2Lengths = new HashMap<Integer, Double>();

    /** Inserts a token into the index. */
    public void insert( String token, int docID, int offset );

    /** Returns the postings for a given term. */
    public PostingsList getPostings( String token );

    /** This method is called on exit. */
    public void cleanup();

}

