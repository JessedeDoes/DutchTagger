JAR=./target/DutchTagger-0.0.1-SNAPSHOT-jar-with-dependencies.jar
CORPUSDIR=/vol1/Corpus
java -Xmx60g -cp $JAR nl.namescape.tagging.ImpactTaggerLemmatizerClient --word2vecFile=$CORPUSDIR/PlainTextDumps/sonar.vectors.bin --tokenize=true $CORPUSDIR/Tagger/Last/withMoreVectorrs BLABLA "$1" "$2"
