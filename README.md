# Google Places

This function sends push notifications to Web, Android, iOS, Windows and Mac devices.

## Usage

You can use it by by adding a function to your Hypi `Query` or `Mutation` types under the schema.
If you don't have these types, add them, if you do, modify them. For example

```graphql
type Query {
    sendNotification(action: String = "send-to-topic", topic: String, message: Json): Json @fn(name: "fcm", version: "v1", src: "hypi", env: ["FCM_SVC_ACC_JSON"])
}
```

This example shows the required parameters for this function. Any of the arguments listed below can be freely added.
The name `sendNotifications` is arbitrary, you can name it anything you like.
The return type is `Json` but you can create a custom type and return that instead. 
Note that the structure of the custom type must match the structure returned from this function.

`FCM_SVC_ACC_JSON` env is required. You create an environment variable in your Hypi app with this name and provide the value on each instance that uses this function.
`action` - must be one of `send`,`send-multiple`,`send-to-topic`,`subscribe`,`unsubscribe`

## Env keys

* `FCM_SVC_ACC_JSON` is required - it is JSON for a Google cloud service account required to access the firebase APIs

## Arguments

The function supports a few variants for sending notifications.
These are all defined by [FCM](https://firebase.google.com/docs/cloud-messaging/concept-options),
their [Message](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#resource:-message) type is central to notifications.
<!-- Generated with https://www.tablesgenerator.com/markdown_tables -->

| Action        | Parameters              | Description                                                                                                                                                                   |
|---------------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| send          |                         | Allows a notification to be sent to 1 device where the token is stored in Hypi                                                                                                |
|               | token_src_type: String  | The name of a GraphQL type in the schema where your app stores device notification tokens                                                                                     |
|               | token_src_field: String | The name of a field within the type given by token_src_type                                                                                                                   |
|               | token_src_id: String    | The ID of the type given by token_src_type                                                                                                                                    |
|               | message                 | The firebase [Message] ( https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#resource:-message ) to send to the token obtained from getting token_src_id |
| subscribe     |                         | Allows notifications to be sent to 1 or more devices where the tokens are stored in Firebase                                                                                  |
|               | topic                   | The topic that the given token should be subscribed to                                                                                                                        |
|               | token                   | The device token. In the future, any notification sent to the topic will be received by the device                                                                            |
| unsubscribe   |                         | Unsubscribe a user/device from a given topic                                                                                                                                  |
|               | topic                   | The topic that the given token should be subscribed to                                                                                                                        |
|               | token                   | The device token. In the future, any notification sent to the topic will be received by the device                                                                            |
| send-to-topic |                         | Send a push notification to this topic. All devices previously subscribed to the topic will receive a notification                                                            |
|               | topic                   | The name of the topic to send the notification to                                                                                                                             |
|               | message                 | The firebase [Message] ( https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages#resource:-message ) to send to the topic                                    |

# Build & Release

1. Make sure you've logged into the Hypi container register by running `docker login hcr.hypi.app -u hypi` and enter a token from your Hypi account as the password
2. Build the JAR and copy the dependencies `mvn clean package`
3. Build the docker image `docker build . -t hcr.hypi.app/public/fcm:v1`
4. Deploy the function `docker push hcr.hypi.app/fcm:v1`

`fcm` is the function name and `v1` is the version. Both are important for using the function later.

As one command:

```shell 
mvn clean package && docker build . -t hcr.hypi.app/public/fcm:v1 && docker push hcr.hypi.app/public/fcm:v1
```
