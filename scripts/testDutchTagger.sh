JAR=./target/DutchTagger-0.0.1-SNAPSHOT-jar-with-dependencies.jar
CORPUSDIR=/vol1/Corpus
LEXICON=/vol1/Corpus/DutchTagger/Data/spelling.tab
java -Xmx60g -cp $JAR nl.namescape.tagging.ImpactTaggerLemmatizerClient --useExternalLexicon true --word2vecFile=$CORPUSDIR/PlainTextDumps/sonar.vectors.bin --tokenize=true $CORPUSDIR/Tagger/Last/withMoreVectorrs $LEXICON "$1" "$2"
