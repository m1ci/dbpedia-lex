FROM hseeberger/scala-sbt:graalvm-ce-21.0.0-java8_1.4.9_2.12.13 AS build

COPY . /lex
WORKDIR /lex
RUN sbt 'set test in assembly := {}' clean assembly

FROM openjdk:11.0.15-oraclelinux7

# these vars are public, must be defined
ENV LEX_LANG=""

# the filenames must be relative to the mounted data folder
ENV LABELS_FILENAME=""
ENV REDIRECTS_FILENAME=""
ENV DISAMBIGUATIONS_FILENAME=""
ENV TEXTLINKS_FILENAME=""

# the var below is private, do not redefine it
# must be mounted to the folder with the data files on host
ENV OUT_FOLDER=/data/

RUN mkdir $OUT_FOLDER
COPY --from=build /lex/target/scala-2.12/dbpedia-lex-assembly-0.1.jar /app/app.jar

SHELL ["/bin/bash", "-c"]
CMD LN=${LABELS_FILENAME:+$OUT_FOLDER$LABELS_FILENAME} && \
    RN=${REDIRECTS_FILENAME:+$OUT_FOLDER$REDIRECTS_FILENAME} && \
    DN=${DISAMBIGUATIONS_FILENAME:+$OUT_FOLDER$DISAMBIGUATIONS_FILENAME} && \
    TN=${TEXTLINKS_FILENAME:+$OUT_FOLDER$WIKILINKS_FILENAME} && \
    java -Dlang_tag=$LEX_LANG -Dlabels_fn=$LN -Dredirects_fn=$RN -Ddisambiguations_fn=$DN -Dtextlinks_fn=$TN -Doutput_folder=$OUT_FOLDER -cp /app/app.jar org.dbpedialex.LexApp
