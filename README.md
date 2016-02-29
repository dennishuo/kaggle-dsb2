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


## Running LocalTool

    mvn clean package
    java -cp lib/ij.jar:target/kaggle-dsb2-1.0.jar org.dhuo.LocalTool ../data/250/study/sax_6

## Areas of investigation

-Given prediction and certainty of prediction, how to arrange shape of CDF
-How to determine location of the LV
  -Static single-image
  -Diffs across timeseries images
-How to measure the area of LV in a slice
  -Pixel count vs circle-based?
-How to do numerical integration of slices
  -Simpsons for quadratic interpolation instead of step or linear.


## TODO

-Remember to add handling of the mixed series like case 123
  -Last number suffix appears to denote different slice locations which may overlap with other sax directories for the same case.
  -Series number still the same within the mixed directory.
  -Dedupe by SeriesNumber-SliceLocation?
-Maybe use niblack instead of naive luminosity threshold for binarization
-At least use dynamic threshold per image if not adaptive dynamic like niblack.
