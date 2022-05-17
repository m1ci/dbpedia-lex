# Lexical Knowledge bases based on data from Wikipedia
NOTE! This is a proof of concept project :). \
The application takes data from [DBpedia](https://www.dbpedia.org) textlinks, labels, disambiguations and redirects, 
processes it and outputs lexical data in the format of [OntoLex Lemon](https://www.w3.org/2019/09/lexicog/) ontology. 

## Running in Docker
The most convenient way of running the processing is Docker.
You should mount a folder with the input data files on a host machine to `/data/` 
directory in a container. \
The container needs the following variables to be specified: \
LEX_LANG - language tag (example: `mk` or `en`) \
LABELS_FILENAME - optional - labels data \
REDIRECTS_FILENAME - optional - redirects data \
DISAMBIGUATIONS_FILENAME - optional - disambiguations data \
TEXTLINKS_FILENAME - optional - textlinks data \
ENABLE_FILTERING - optional - default: false - filter out unreliable data based on frequency \

!!! NOTE !!! The filenames must be relative to the mounted data folder.

Here is an example.
```shell script
# first build a docker container
docker build . --tag lex
# second run it
docker run -v <folder with the data on host>:/data/ -e LEX_LANG=<language tag> -e ENABLE_FILTERING=false -e LABELS_FILENAME=<labels file> -e REDIRECTS_FILENAME=<redirects file> -e DISAMBIGUATIONS_FILENAME=<disambiguations file> -e TEXTLINKS_FILENAME=<text links file> lex
```

The results will be output in N-Triples format in the mounted data folder under the language tag, i.e `/data/mk/`. 