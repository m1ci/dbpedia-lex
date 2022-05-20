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
The following prefixes were used in all the outputs:
```
@prefix dct:   <http://purl.org/dc/terms/> .
@prefix ontolex: <http://www.w3.org/ns/lemon/ontolex#> .
@prefix lexinfo: <http://www.lexinfo.net/ontology/2.0/lexinfo#> .
@prefix lex:   <https://dbpedia.org/lex/mk/> .
@prefix frac:  <http://www.w3.org/nl/lemon/frac#> .
```

### Textlinks

Input:
```
<http://mk.dbpedia.org/resource/Папа_Стефан_VII?dbpv=2020-02&nif=phrase&char=1349,1356> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Папа_Павле_I> .
<http://mk.dbpedia.org/resource/Папа_Стефан_VII?dbpv=2020-02&nif=phrase&char=1349,1356> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "Павле I" .
<http://mk.dbpedia.org/resource/Нидерцимерн?dbpv=2020-02&nif=word&char=13,22> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Германски_јазик> .
<http://mk.dbpedia.org/resource/Нидерцимерн?dbpv=2020-02&nif=word&char=13,22> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "германски" .
<http://mk.dbpedia.org/resource/NGC_472?dbpv=2020-02&nif=phrase&char=1246,1278> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Каталог_на_најважните_галаксии> .
<http://mk.dbpedia.org/resource/NGC_472?dbpv=2020-02&nif=phrase&char=1246,1278> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "Каталогот на најважните галаксии" .
<http://mk.dbpedia.org/resource/ФК_Вотфорд?dbpv=2020-02&nif=phrase&char=4899,4909> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/ФК_Стоук_Сити> .
<http://mk.dbpedia.org/resource/ФК_Вотфорд?dbpv=2020-02&nif=phrase&char=4899,4909> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "Стоук Сити" .
<http://mk.dbpedia.org/resource/Марк_Јулијано?dbpv=2020-02&nif=phrase&char=1563,1582> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/УЕФА_Супер_Куп> .
<http://mk.dbpedia.org/resource/Марк_Јулијано?dbpv=2020-02&nif=phrase&char=1563,1582> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "Суперкуп на Европа" .
<http://mk.dbpedia.org/resource/Синевир?dbpv=2020-02&nif=word&char=3847,3851> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Волк> .
<http://mk.dbpedia.org/resource/Синевир?dbpv=2020-02&nif=word&char=3847,3851> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "волк" .
<http://mk.dbpedia.org/resource/Call_of_Duty:_Modern_Warfare_3?dbpv=2020-02&nif=word&char=5408,5417> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Викицитат> .
<http://mk.dbpedia.org/resource/Call_of_Duty:_Modern_Warfare_3?dbpv=2020-02&nif=word&char=5408,5417> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "Викицитат" .
<http://mk.dbpedia.org/resource/NGC_6689?dbpv=2020-02&nif=word&char=1010,1021> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Деклинација_(астрономија)> .
<http://mk.dbpedia.org/resource/NGC_6689?dbpv=2020-02&nif=word&char=1010,1021> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "деклинација" .
<http://mk.dbpedia.org/resource/Меѓународен_ден_на_мајчиниот_јазик?dbpv=2020-02&nif=phrase&char=62,77> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/Мајчин_јазик> .
<http://mk.dbpedia.org/resource/Меѓународен_ден_на_мајчиниот_јазик?dbpv=2020-02&nif=phrase&char=62,77> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "мајчиниот јазик" .
<http://mk.dbpedia.org/resource/Адвентисти?dbpv=2020-02&nif=word&char=3531,3535> <http://www.w3.org/2005/11/its/rdf#taIdentRef> <http://mk.dbpedia.org/resource/1844> .
<http://mk.dbpedia.org/resource/Адвентисти?dbpv=2020-02&nif=word&char=3531,3535> <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#anchorOf> "1844" .
```

#### Synonyms

Output RDF:
```
lex:cf_БМВ  ontolex:writtenRep  "БМВ"@mk .

lex:cf_BMW  ontolex:writtenRep  "BMW"@mk .

lex:ls_BMW_sense1  a       ontolex:LexicalSense ;
        lexinfo:synonym    lex:ls_БМВ_sense1 ;
        frac:frequency     [ a            frac:CorpusFrequency ;
                             <http://www.w3.org/1999/02/22-rdf-syntax-ns#value>
                                     "1"^^<http://www.w3.org/2001/XMLSchema#int> ;
                             frac:corpus  <https://databus.dbpedia.org/dbpedia/text/nif-text-links/>
                           ] ;
        ontolex:reference  <http://mk.dbpedia.org/resource/БМВ> .

lex:le_BMW  a                  ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_BMW ;
        ontolex:sense          lex:ls_BMW_sense1 .

lex:ls_БМВ_sense1  a       ontolex:LexicalSense ;
        lexinfo:synonym    lex:ls_BMW_sense1 ;
        frac:frequency     [ a            frac:CorpusFrequency ;
                             <http://www.w3.org/1999/02/22-rdf-syntax-ns#value>
                                     "18"^^<http://www.w3.org/2001/XMLSchema#int> ;
                             frac:corpus  <https://databus.dbpedia.org/dbpedia/text/nif-text-links/>
                           ] ;
        ontolex:reference  <http://mk.dbpedia.org/resource/БМВ> .

lex:le_БМВ  a                  ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_БМВ ;
        ontolex:sense          lex:ls_БМВ_sense1 .
```

#### Polysemantic words

Output RDF:
```
lex:cf_житата  ontolex:writtenRep  "житата"@mk .

lex:le_житата  a               ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_житата ;
        ontolex:sense          lex:ls_житата_sense1 , lex:ls_житата_sense2 , lex:ls_житата_sense3 .

lex:ls_житата_sense3  a    ontolex:LexicalSense ;
        frac:frequency     [ a            frac:CorpusFrequency ;
                             <http://www.w3.org/1999/02/22-rdf-syntax-ns#value>
                                     "3"^^<http://www.w3.org/2001/XMLSchema#int> ;
                             frac:corpus  <https://databus.dbpedia.org/dbpedia/text/nif-text-links/>
                           ] ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Жито> .

lex:ls_житата_sense1  a    ontolex:LexicalSense ;
        frac:frequency     [ a            frac:CorpusFrequency ;
                             <http://www.w3.org/1999/02/22-rdf-syntax-ns#value>
                                     "1"^^<http://www.w3.org/2001/XMLSchema#int> ;
                             frac:corpus  <https://databus.dbpedia.org/dbpedia/text/nif-text-links/>
                           ] ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Житарица> .

lex:ls_житата_sense2  a    ontolex:LexicalSense ;
        frac:frequency     [ a            frac:CorpusFrequency ;
                             <http://www.w3.org/1999/02/22-rdf-syntax-ns#value>
                                     "1"^^<http://www.w3.org/2001/XMLSchema#int> ;
                             frac:corpus  <https://databus.dbpedia.org/dbpedia/text/nif-text-links/>
                           ] ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Житни_култури> .
```
### Labels

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

### Disambiguations

Input:
```
<http://mk.dbpedia.org/resource/Самба_(појаснување)> <http://dbpedia.org/ontology/wikiPageDisambiguates> <http://mk.dbpedia.org/resource/Самба> .
<http://mk.dbpedia.org/resource/Самба_(појаснување)> <http://dbpedia.org/ontology/wikiPageDisambiguates> <http://mk.dbpedia.org/resource/Самба_(софтвер)> .
<http://mk.dbpedia.org/resource/Самба_(појаснување)> <http://dbpedia.org/ontology/wikiPageDisambiguates> <http://mk.dbpedia.org/resource/Самба_(танц)> .
```

Output RDF graph:
```
<https://dbpedia.org/lex/mk/ls_Самба_(танц)_sense1>
        a                  ontolex:LexicalSense ;
        dct:subject        "танц"@mk ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Самба_(танц)> .

<https://dbpedia.org/lex/mk/ls_Самба_(софтвер)_sense1>
        a                  ontolex:LexicalSense ;
        dct:subject        "софтвер"@mk ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Самба_(софтвер)> .

lex:cf_Самба  ontolex:writtenRep  "Самба"@mk .

lex:ls_Самба_sense1  a     ontolex:LexicalSense ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Самба> .

lex:le_Самба  a                ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_Самба ;
        ontolex:sense          <https://dbpedia.org/lex/mk/ls_Самба_(танц)_sense1> , <https://dbpedia.org/lex/mk/ls_Самба_(софтвер)_sense1> , lex:ls_Самба_sense1 .
```

### Redirects

Input:
```
<http://mk.dbpedia.org/resource/Алфонсо_ди_Борџија> <http://dbpedia.org/ontology/wikiPageRedirects> <http://mk.dbpedia.org/resource/Папа_Каликст_III> .
<http://mk.dbpedia.org/resource/Папа_Каликстиј_III> <http://dbpedia.org/ontology/wikiPageRedirects> <http://mk.dbpedia.org/resource/Папа_Каликст_III> .
<http://mk.dbpedia.org/resource/Папа_Каликстус_III> <http://dbpedia.org/ontology/wikiPageRedirects> <http://mk.dbpedia.org/resource/Папа_Каликст_III> .
```

Output RDF:
```
lex:cf_Алфонсо_ди_Борџија
        ontolex:writtenRep  "Алфонсо ди Борџија"@mk .

lex:le_Алфонсо_ди_Борџија
        a                      ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_Алфонсо_ди_Борџија ;
        ontolex:sense          lex:ls_Алфонсо_ди_Борџија_sense1 .

lex:ls_Папа_Каликстус_III_sense1
        a                  ontolex:LexicalSense ;
        lexinfo:synonym    lex:ls_Папа_Каликстиј_III_sense1 , lex:ls_Алфонсо_ди_Борџија_sense1 , lex:ls_Папа_Каликст_III_sense1 ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Папа_Каликст_III> .

lex:ls_Папа_Каликстиј_III_sense1
        a                  ontolex:LexicalSense ;
        lexinfo:synonym    lex:ls_Папа_Каликстус_III_sense1 , lex:ls_Алфонсо_ди_Борџија_sense1 , lex:ls_Папа_Каликст_III_sense1 ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Папа_Каликст_III> .

lex:ls_Папа_Каликст_III_sense1
        a                  ontolex:LexicalSense ;
        lexinfo:synonym    lex:ls_Папа_Каликстус_III_sense1 , lex:ls_Папа_Каликстиј_III_sense1 , lex:ls_Алфонсо_ди_Борџија_sense1 ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Папа_Каликст_III> .

lex:cf_Папа_Каликстиј_III
        ontolex:writtenRep  "Папа Каликстиј III"@mk .

lex:cf_Папа_Каликстус_III
        ontolex:writtenRep  "Папа Каликстус III"@mk .

lex:le_Папа_Каликстиј_III
        a                      ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_Папа_Каликстиј_III ;
        ontolex:sense          lex:ls_Папа_Каликстиј_III_sense1 .

lex:cf_Папа_Каликст_III
        ontolex:writtenRep  "Папа Каликст III"@mk .

lex:le_Папа_Каликст_III
        a                      ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_Папа_Каликст_III ;
        ontolex:sense          lex:ls_Папа_Каликст_III_sense1 .

lex:ls_Алфонсо_ди_Борџија_sense1
        a                  ontolex:LexicalSense ;
        lexinfo:synonym    lex:ls_Папа_Каликстус_III_sense1 , lex:ls_Папа_Каликстиј_III_sense1 , lex:ls_Папа_Каликст_III_sense1 ;
        ontolex:reference  <http://mk.dbpedia.org/resource/Папа_Каликст_III> .

lex:le_Папа_Каликстус_III
        a                      ontolex:LexicalEntry ;
        ontolex:canonicalForm  lex:cf_Папа_Каликстус_III ;
        ontolex:sense          lex:ls_Папа_Каликстус_III_sense1 .
```





