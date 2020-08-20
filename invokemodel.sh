#!/bin/bash
# author:zdj
# url:http://cs.xmut.edu.cn/szjs/szdw/201709/t20170928_202779.html
cd $2
rm -rf *.pot
if [ $# -gt 4 ]
then
for ((i=5;i<=$#;i++))
do
echo here
eval ln -s $1pointsource/${!i}$4.pot $2${!i}0000.pot
done
fi
rm -rf fig.fig
ln -s $1config/$3"0000.fig" $2fig.fig
#exist point source or not
cd $2
$2swat.out
mv $2$3"0000.pot" $1pointsource/$3$4.pot
