JAR=./target/DutchTagger-0.0.1-SNAPSHOT-jar-with-dependencies.jar

CORPUSDIR=/media/jesse/Data/Diamant
LEXICON=$CORPUSDIR/DutchTagger/Data/spelling.tab
VECTORS=$CORPUSDIR/DutchTagger/Data/sonar.vectors.bin
MODEL=$CORPUSDIR/DutchTagger/Data/withMoreVectorrs

nice -n19 java -Xmx20g -cp $JAR nl.namescape.tagging.ImpactTaggerLemmatizerClient --useExternalLexicon true --word2vecFile=$VECTORS --tokenize=true $MODEL $LEXICON "$1" "$2"
