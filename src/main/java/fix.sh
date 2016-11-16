for x in `find . -name "*.java"`;
do
  perl -pe 's/System.err/nl.openconvert.log.ConverterLog.defaultLog/g' $x > $x.logfix;
  mv $x.logfix $x;
done

