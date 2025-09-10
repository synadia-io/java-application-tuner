# Synadia Java Application Tuner

This repository contains customizable code to help you tune your system.

To build, run gradle to create an uberjar. The version with this project is 6.8.3

```
gradlew clean uberJar 
```

### Connection Tuning

Small application that can help tune the connection in regard to the publish/write side. To run:

```
java -cp build/libs/tuning-1.0.0-uber.jar io.nats.tuning.connection.MainConnectionTune
```

### Subscription and Consumer

Currently, when starting up a large number of ephemeral consumers when your app starts up
as large number of these may take some time to complete, depending on your parallelization and volume.

To run:

```
java -cp build/libs/tuning-1.0.0-uber.jar io.nats.tuning.consumercreate.MainConsumerCreate
```
___

Copyright (c) 2021-2025 Synadia Communications Inc.  All Rights Reserved.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
