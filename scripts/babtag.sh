
DATADIR=/mnt/Projecten/Taalbank/Namescape/Succeed/NamescapeInstallationPackage/NamescapeData/
DATADIR=/vol1/Temp/Giza/Data/
tokenize=true
modernLexicon=$DATADIR/MapDB/Modern.mapdb
historicalLexicon=$DATADIR/MapDB/Historical.mapdb
patternInput=$DATADIR/dulem/multigrams.cleaner
lexiconTrie=$DATADIR/Dutch/modernWords.datrie
taggingModel=$DATADIR/bab.model
JAR=target/DutchTagger-0.0.1-SNAPSHOT-jar-with-dependencies.jar
#JAVA=/opt/jdk8/jdk1.8.0_40/bin/java

java -classpath $JAR nl.namescape.tagging.TaggerLookupLemmatizerClient\
	--taggingModel=$taggingModel\
	--modernLexicon=$modernLexicon\
	--historicalLexicon=$historicalLexicon\
	--patternInput=$patternInput\
	--lexiconTrie=$lexiconTrie\
	--useMatcher=true\
	$1 $2
