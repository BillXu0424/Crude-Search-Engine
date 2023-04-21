package ir;

import java.util.HashMap;
import java.util.Iterator;


/**
 *   Implements an inverted index as a Hashtable from words to PostingsLists.
 */
public class HashedIndex implements Index {


    /** The index as a hashtable. */
    private HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();


    /**
     *  Inserts this token in the hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        //
        // YOUR CODE HERE
        //
        PostingsEntry newEntry = new PostingsEntry(docID);

        if (getPostings(token) == null) {
            PostingsList newList = new PostingsList();
            newList.addPostingsEntry(newEntry, offset);
            index.put(token, newList);
        }
        else {
            PostingsList existedList = getPostings(token);
            existedList.addPostingsEntry(newEntry, offset);
        }
    }


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        //
        // REPLACE THE STATEMENT BELOW WITH YOUR CODE
        //
        return index.get(token);
    }


    /**
     *  No need for cleanup in a HashedIndex.
     */
    public void cleanup() {
    }
}
