rem complete your IDEA Home folder here
call setHomeDirs.bat

set IDEA_VERSION=129.1359
rem set IDEA_VERSION=129.1359
set MKS_VERSION=8.3.2392
set IDEA_GROUP_ID=com.intellij

call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\openapi.jar" -DartifactId=openapi -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\idea.jar" -DartifactId=idea -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\annotations.jar" -DartifactId=annotations -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\extensions.jar" -DartifactId=extensions -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\util.jar" -DartifactId=util -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\javac2.jar" -DartifactId=javac2 -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\asm4-all.jar" -DartifactId=asm4-all -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\jgoodies-forms.jar" -DartifactId=jgoodies-forms -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\jdom.jar" -DartifactId=jdom -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=%IDEA_GROUP_ID% -Dpackaging=jar -Dfile="%IDEA_HOME%\lib\jgoodies-forms.jar" -DartifactId=jgoodies-forms -Dversion=%IDEA_VERSION%
call mvn install:install-file -DgroupId=com.mks -Dpackaging=jar -Dfile="lib\%MKS_VERSION%\mksapi-without-commons.jar" -DartifactId=mksapi-without-commons -Dversion=%MKS_VERSION%
