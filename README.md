# kaggle-dsb2
Kaggle 2nd annual data science bowl

## Setup

    sudo apt-get update
    sudo apt-get -y install ant
    sudo apt-get -y install openjdk-8-jdk
    sudo apt-get -y install git vim unzip
    sudo apt-get -y install build-essential
    sudo apt-get -y install cmake git libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev
    sudo apt-get -y install python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev
    sudo apt-get -y install imagej

    # Installing imagej adds /usr/share/java/ij.jar which we'll include in mvn and -cp.

### Build opencv

OpenCV is harder to get working as a Java dependency than it ought to be. We must build from source.

http://docs.opencv.org/3.0-last-rst/doc/tutorials/introduction/linux_install/linux_install.html

    wget -O opencv-3.0.0.zip "https://github.com/Itseez/opencv/archive/3.0.0.zip"
    unzip opencv-3.0.0.zip
    cd opencv-3.0.0
    mkdir build
    cd build
    export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
    cmake -D CMAKE_BUILD_TYPE=Release -D CMAKE_INSTALL_PREFIX=/usr/local ..
    make -j7

The bin/opencv-3.0.0.jar should be added to the Java classpath for both compilation and running.
The lib/ files will need to be made available on java.library.path.


## Running Local tools

    mvn clean package
    java -cp lib/ij.jar:target/kaggle-dsb2-1.0.jar org.dhuo.LocalTool ../data/250/study/sax_6
    java -cp target/kaggle-dsb2-1.0.jar org.dhuo.Reconstructor ../data/train_series_results_20160302_0.csv
    java -cp ~/Downloads/commons-math3-3.2.jar:target/kaggle-dsb2-1.0.jar org.dhuo.SubmissionWriter ../data/train_spark0_presubmit.csv ../data/train_spark0_presubmit.csv  ../data/train.csv > ../data/train_spark0_adj_auto.csv
    java -cp ~/Downloads/commons-math3-3.2.jar:target/kaggle-dsb2-1.0.jar org.dhuo.Scorer ../data/train_spark0_adj_auto.csv ../data/train.csv

## Running DistribTool on local Spark

    spark-submit --master local[1] --jars lib/ij.jar --class org.dhuo.DistribTool target/kaggle-dsb2-1.0.jar file:///home/user/projex/kaggle-dsb2/data4 foop.csv

## Running on Dataproc

    # Due to DICOM.java unwisely using "private static Properties dictionary", need spark.executor.cores=1 and to adjust memory accordingly.
    gcloud dataproc jobs submit spark --cluster dhuo-dcm-20160302-0 --properties spark.executor.cores=1,spark.executor.memory=1500m --jars gs://dhuo-gce/ij.jar,target/kaggle-dsb2-1.0.jar --class org.dhuo.DistribTool gs://dhuo-kaggle-dsb2/train_data/train gs://dhuo-kaggle-dsb2/train_series_results_20160302_0.csv

## Areas of investigation

-Given prediction and certainty of prediction, how to arrange shape of CDF
-How to determine location of the LV
  -Static single-image
  -Diffs across timeseries images
-How to measure the area of LV in a slice
  -Pixel count vs circle-based?
-How to do numerical integration of slices
  -Simpsons for quadratic interpolation instead of step or linear.

## DONE

-Linear regression for raw estimates to observed, then generate CDFs based on observed variance
-Add demuxing of cases like 123
-Add de-duping of same slice location

## TODO

-Reconstruction Time
  -Detect bogus slice info if adjacent slices disagree on approx position of LV
  -How to estimate missed slices near the LA.
-Image feature time
  -Maybe use niblack instead of naive luminosity threshold for binarization
  -At least use dynamic threshold per image if not adaptive dynamic like niblack.
  -Missed slices near the LA may have been missed due to disappearing of walls at systole; maybe check shorter sub snippets
  -Maybe back out elliptical approximation instead of pure pixel count
  -Incorporate 2ch and/or 4ch
