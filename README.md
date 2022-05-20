# Lexical Knowledge bases based on data from Wikipedia
NOTE! This is a proof of concept project :). \
The application takes data from [DBpedia](https://www.dbpedia.org) [textlinks](https://databus.dbpedia.org/dbpedia/text/nif-text-links/), [labels](https://databus.dbpedia.org/dbpedia/generic/labels/), [disambiguations](https://databus.dbpedia.org/dbpedia/generic/disambiguations/) and [redirects](https://databus.dbpedia.org/dbpedia/generic/redirects/), 
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

## Examples of input data and output models
### labels

Input:
```
<http://mk.dbpedia.org/resource/Бенцов_автомобил> <http://www.w3.org/2000/01/rdf-schema#label> "Бенцов автомобил"@mk .
```

Output RDF graph:
```
lex:ls_Бенцов_автомобил_sense1
        a                  ontolex:LexicalSense ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Бенцов_автомобил> .

lex:le_Бенцов_автомобил
        a                      ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_Бенцов_автомобил ;
        ontolex:sense          lex:ls_Бенцов_автомобил_sense1 .

lex:cf_Бенцов_автомобил
        ontolex:writtenRep  "Бенцов автомобил"@mk .
```



