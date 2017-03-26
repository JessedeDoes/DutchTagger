source setClassPath.sh
DIR=/mnt/Projecten/Taalbank/CL-SE-data/Corpora/LexiconToolCorpora/Zeebrieven/Vertical/TC
TESTSET=$DIR/test_corpus
Lexicon=/mnt/Projecten/Taalbank/Namescape/Succeed/NamescapeInstallationPackage/hilex.tab
java -Djava.library.path=./lib -Xmx4g 'impact.ee.tagger.BasicTagger$Tester' Models/bab.model $TESTSET
