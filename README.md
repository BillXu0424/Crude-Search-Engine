# Crude-Search-Engine

## Introduction

This small search engine is written in Java and provides a simple yet powerful way to search through a corpus of documents.

When the engine starts, it reads the corpus stored on the disk, and use regex to tokenize the documents, and then constructs an inverted index. This allows for fast and efficient searching. The inverted index maps each term in the corpus to a list of documents that contain that term. This makes it easy to find all the documents that match a given query.

It supports simple phrase queries as well as ranked queries based on PageRank and tf-idf. PageRank is an algorithm used by Google to rank web pages in their search engine results. It measures the importance of a page based on the number and quality of links pointing to it. Tf-idf, on the other hand, stands for term frequency-inverse document frequency. It is a numerical statistic used to reflect how important a word is to a document in a collection or corpus.

In addition to these powerful search features, we also provide a spelling checker to help you find what you're looking for even if you make a typo. Our spelling checker uses an algorithm to suggest corrections for misspelled words.

## Run

1. Prepare the corpus (text files) in `../[folder names]`.
2. Paste the folder path of the corpus into `./run_search_engine.bat`.
3. Run `./compile.bat` and `./run_search_engine.bat`.