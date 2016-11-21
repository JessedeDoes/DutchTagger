JAR=./target/DutchTagger-0.0.1-SNAPSHOT-jar-with-dependencies.jar
RESOURCE=src/main/resources/
PerseusLexicon=resources/exampledata/perseus/perseus.lex.tab
#Training=resources/exampledata/proiel/proiel.train
Training=$RESOURCE/resources/exampledata/perseus/treebank.coarse.train
Model=Models/latin.proiel.model
java -classpath $JAR -Xmx4g 'impact.ee.tagger.BasicTagger$Trainer' -l$PerseusLexicon $Training $Model
