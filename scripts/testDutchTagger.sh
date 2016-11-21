JAR=./target/DutchTagger-0.0.1-SNAPSHOT-jar-with-dependencies.jar

CORPUSDIR=/vol1/Corpus
LEXICON=/vol1/Corpus/DutchTagger/Data/spelling.tab
VECTORS=$CORPUSDIR/DutchTagger/Data/sonar.vectors.bin
MODEL=$CORPUSDIR/DutchTagger/Data/withMoreVectorrs

java -Xmx60g -cp $JAR nl.namescape.tagging.ImpactTaggerLemmatizerClient --useExternalLexicon true --word2vecFile=$VECTORS --tokenize=true $MODEL $LEXICON "$1" "$2"
