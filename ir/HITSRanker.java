package ir;

import java.util.*;

import javax.xml.datatype.DatatypeConfigurationException;

import java.io.*;


public class HITSRanker {

    /**
     *   Max number of iterations for HITS
     */
    final static int MAX_NUMBER_OF_STEPS = 1000;

    /**
     *   Convergence criterion: hub and authority scores do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.001;

    /**
     *   The inverted index
     */
    Index index;

    /**
     *   Mapping from the titles to internal document ids used in the links file
     */
    HashMap<String,Integer> titleToId = new HashMap<String,Integer>();

    /**
     *   Mapping from internal document ids used in the links file to titles
     */
    HashMap<Integer, String> idToTitle = new HashMap<Integer, String>();

    /**
     *   Sparse vector containing hub scores
     */
    HashMap<Integer,Double> hubs = new HashMap<Integer, Double>();

    /**
     *   Sparse vector containing authority scores
     */
    HashMap<Integer,Double> authorities = new HashMap<Integer, Double>();

    /**
     *   A memory-efficient representation of graph links.
     */
    HashMap <Integer, HashMap<Integer, Boolean>> link = new HashMap<Integer, HashMap<Integer, Boolean>>();

    /**
     *   Inverse link: from toNode to fromNode
     */
    HashMap <Integer, HashMap<Integer, Boolean>> inverseLink = new HashMap<Integer, HashMap<Integer, Boolean>>();

    
    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph.
     * Each page is a node in graph with a distinct nodeID associated with it.
     * There is an edge between two nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     *  nodeID;outNodeID1,outNodeID2,...,outNodeIDK
     * This means that there are edges between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format:
     *  nodeID;pageTitle
     *  
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the same
     *       as docIDs used by search engine's Indexer
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     * @param      index           The inverted index
     */
    public HITSRanker( String linksFilename, String titlesFilename, Index index ) {
        this.index = index;
        readDocs( linksFilename, titlesFilename );
    }


    /* --------------------------------------------- */

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


    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     */
    void readDocs( String linksFilename, String titlesFilename ) {
        BufferedReader brLinks;
		try {
			brLinks = new BufferedReader(new FileReader(linksFilename));
			String line = brLinks.readLine();

			while (line != null) {
                String[] splits = line.split(";");
                int from = Integer.parseInt(splits[0]);
                link.put(from, new HashMap<Integer, Boolean>());
                if (splits.length > 1) {
                    String[] tos = splits[1].split(",");
                    for (String to : tos) {
                        /* update link */
                        link.get(from).put(Integer.parseInt(to), true);
                        /* update inversLink */
                        if (inverseLink.containsKey(Integer.parseInt(to))) {
                            inverseLink.get(Integer.parseInt(to)).put(from, true);
                        }
                        else {
                            inverseLink.put(Integer.parseInt(to), new HashMap<Integer, Boolean>());
                        }
                    }
                }
				line = brLinks.readLine();
			}

			brLinks.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

        BufferedReader brTitles;
        try {
			brTitles = new BufferedReader(new FileReader(titlesFilename));
			String line = brTitles.readLine();

			while (line != null) {
                int idx = line.indexOf( ";" );
                int docId = Integer.parseInt(line.substring(0, idx));
                String title = line.substring(idx + 1);
                titleToId.put(title, docId);
                idToTitle.put(docId, title);
				line = brTitles.readLine();
			}

			brTitles.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    /**
     * Perform HITS iterations until convergence
     *
     * @param      titles  The titles of the documents in the base set
     */
    private void iterate(String[] titles) {
        HashSet<Integer> internalIDs = new HashSet<>();
        for (String title : titles) { 
            internalIDs.add(titleToId.get(title));
        }
        iterate(internalIDs);
    }

    /**
     * Perform HITS iterations until convergence
     * @param internalIDs The internal IDs of the documents in the base set
     */
    private void iterate(HashSet<Integer> internalIDs) {
        /* initialize hub score and authority score to 1 */
        hubs = new HashMap<>();
        authorities = new HashMap<>();
        for (int id : internalIDs) {
            hubs.put(id, 1.0);
            authorities.put(id, 1.0);
        }
        /* iterate */
        double error = Double.MAX_VALUE;
        while (error > EPSILON) {
            HashMap<Integer, Double> newHubs = new HashMap<>(hubs);
            HashMap<Integer, Double> newAuthorities = new HashMap<>(authorities);
            for (int node : newHubs.keySet()) { 
                newHubs.put(node, 0.0);
                newAuthorities.put(node, 0.0);
            }
            for (int from : hubs.keySet()) {
                HashMap<Integer, Boolean> tos = link.get(from);
                if (tos != null) {
                    for (int to : tos.keySet()) {
                        if (hubs.containsKey(to)) {
                            newHubs.put(from, newHubs.get(from) + authorities.get(to));
                            newAuthorities.put(to, newAuthorities.get(to) + hubs.get(from));
                        }     
                    }
                }
            }
            /* normalize */
            double hubsNormalizer = 0.0;
            double authoritiesNormalizer = 0.0;
            for (int node : newHubs.keySet()) {
                hubsNormalizer += Math.pow(newHubs.get(node), 2);
                authoritiesNormalizer += Math.pow(newAuthorities.get(node), 2);
            }
            hubsNormalizer = Math.sqrt(hubsNormalizer);
            authoritiesNormalizer = Math.sqrt(authoritiesNormalizer);

            double temp = 0.0;
            for (int node : newHubs.keySet()) {
                double normalizedHub = newHubs.get(node) / hubsNormalizer;
                double normalizedAuthorities = newAuthorities.get(node) / authoritiesNormalizer;
                temp = Math.max(temp, Math.max(Math.abs(normalizedHub - hubs.get(node)), Math.abs(normalizedAuthorities - authorities.get(node))));
                newHubs.put(node, normalizedHub);
                newAuthorities.put(node, normalizedAuthorities);
            }
            error = temp;
            /* update */
            hubs = new HashMap<>(newHubs);
            authorities = new HashMap<>(newAuthorities);
        }
    }


    /**
     * Rank the documents in the subgraph induced by the documents present
     * in the postings list `post`.
     *
     * @param      post  The list of postings fulfilling a certain information need
     *
     * @return     A list of postings ranked according to the hub and authority scores.
     */
    PostingsList rank(PostingsList post) {
        /* generate induced base set from root set */
        HashSet<Integer> baseSet = new HashSet<>();
        for (int i = 0; i < post.size(); i++) {
            int node = titleToId.get(getFileName(index.docNames.get(post.get(i).docID)));
            baseSet.add(node);
            if (link.get(node) != null) {
                for (int to : link.get(node).keySet()) {
                    baseSet.add(to);
                }
            }
            if (inverseLink.get(node) != null) {
                for (int from : inverseLink.get(node).keySet()) {
                    baseSet.add(from);
                }
            }
        } 
        /* perform HITS on base set */
        iterate(baseSet);
        /* combine hub score and authority score */
        ArrayList<PostingsEntry> result = new ArrayList<>();
        for (int node : hubs.keySet()) {
            /* choose from the maximum in hub and authority score */
            double score = Math.max(hubs.get(node), authorities.get(node));
            result.add(new PostingsEntry(index.docIdentifiers.get(getFileName(idToTitle.get(node))), score));
        }
        /* rank */
        Collections.sort(result, new PostingsEntry.scoreComparator());
        return new PostingsList(result);
    }


    /**
     * Sort a hash map by values in the descending order
     *
     * @param      map    A hash map to sorted
     *
     * @return     A hash map sorted by values
     */
    private HashMap<Integer,Double> sortHashMapByValue(HashMap<Integer,Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer,Double> > list = new ArrayList<Map.Entry<Integer,Double> >(map.entrySet());
      
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) { 
                    return (o2.getValue()).compareTo(o1.getValue()); 
                } 
            }); 
              
            HashMap<Integer,Double> res = new LinkedHashMap<Integer,Double>(); 
            for (Map.Entry<Integer,Double> el : list) { 
                res.put(el.getKey(), el.getValue()); 
            }
            return res;
        }
    } 


    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param      map        A hash map
     * @param      fname      The filename
     * @param      k          A number of entries to write
     */
    void writeToFile(HashMap<Integer,Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
            
            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer,Double> e : map.entrySet()) {
                    i++;
                    writer.write(e.getKey() + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k) break;
                }
            }
            writer.close();
        } catch (IOException e) {}
    }


    /**
     * Rank all the documents in the links file. Produces two files:
     *  hubs_top_30.txt with documents containing top 30 hub scores
     *  authorities_top_30.txt with documents containing top 30 authority scores
     */
    void rank() {
        iterate(titleToId.keySet().toArray(new String[0]));
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }


    /* --------------------------------------------- */


    public static void main( String[] args ) {
        if ( args.length != 2 ) {
            System.err.println( "Please give the names of the link and title files" );
        }
        else {
            HITSRanker hr = new HITSRanker( args[0], args[1], null );
            hr.rank();
        }
    }
} 