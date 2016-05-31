#Compiles code, jar-ifies it and runs it.
#by Liam O'Flynn.

./gradlew shadowJar &&
echo 
echo "gradlew shadowJar completed succesfully. Executing DwarBot-all.jar..."
echo  
cd build/libs/ && 
java -jar DwarfBot-all.jar
echo
echo "...Complete."

