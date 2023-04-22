# Call monitoring system

This is a demo app for testing Scala with Akka framework created as a Scala Academy Assessment project. You can find the work 
assignment [here](https://hotovo.jira.com/wiki/spaces/HTO/pages/3960995859/Scala+Academy+Assessment+project). The assignment 
has been slightly accommodated.

### Dependencies needed:
- Docker (download [here](https://www.docker.com/products/docker-desktop/))
- IDE (download IntelliJ Idea [here](https://www.jetbrains.com/idea/download/?fromIDE=#section=windows) - Community Edition is enough)
- Postman (download [here](https://www.postman.com/downloads/))

### How to run the program:

1. Open project in your favourite IDE
2. From terminal run command: `docker-compose up`. It will start cassandra in docker
3. Run App from [here](src/main/scala/sk/glova/monitoringsystem/app/App.scala)
4. Run calls from Postman (you can import prepared [collection](postman_collection.json) into Postman)
