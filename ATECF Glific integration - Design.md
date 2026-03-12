## **Overview**

The primary problem statement is that a lot of users do not log in immediately after completing their learning modules on Glific. Pushing them to start using the app is effort intensive, and is affecting regular monitoring of the desilting work. 

This project is to try to see if timely nudges provided to them can get them on the right track without needing human intervention. Nudges will be sent by Glific, but the conditions are known only to Avni. While Avni will act as the primary source of triggers, Glific remains the nudger. 

## **Design**

### **Components affected**

1. A flow log table in Avni database that stores flows triggered in Glific whenever a new flow request is triggered from Avni  
2. Enhancement of integration-service to include new triggers to be sent  
3. Reports for daily monitoring of cases that do not work even after nudges, and a view of how well the nudges are working

### **Details**

Nudges that can help are provided [here](https://docs.google.com/spreadsheets/d/1T-32lXV-GJDav7IPvxcyHImMkThYv146JDvogT9nw58/edit?usp=sharing). These nudges can be modeled as independent triggers, the only dependency being existing data in Avni and the history of nudges already sent (which needs to be built). 

1. Logging \- A record of all flows triggered will be recorded in a new table \- flow\_request\_queue (actually a log, not a queue). This is a platform change, and will need to be performed on the version that is expected to be deployed to RWB. Information from the log will also be used to ensure that messages are not sent multiple times  
2. There is a [integration-service](https://github.com/avniproject/integration-service) module deployed in the RWB infrastructure that runs on a schedule. This is currently used to push Glific triggers when a user is created. We will enhance this service to include the new nudge conditions. Nudge conditions need to be added separately for each org within rwb because integration-service works based on org logins, and we have 3 orgs  
3. The integration-service will be scheduled to run every 30 minutes, triggering flows that need to be triggered at that time. Since some of these conditions happen offline, we might not be able to send messages immediately, but the conditions will be written in such a way that conditions evaluated will be valid nevertheless  
4. System monitoring \- There is existing monitoring for any situation where integration-service goes down, and alerts are sent to the Avni product team in such cases. These will be dealt with on an immediate basis during working hours  
5. Rerunnability \- If a message send fails, then the log information will be reverted. This ensures that failed jobs can be rerun without the worry of sending messages or flows twice  
6. Daily monitoring reports \- New reports will be introduced with details available in the reports to monitor these users. Human oversight is required if data does not enter the system in spite of pushing people through automatic triggers on Whatsapp. The hope is that the system nudges will push people into doing things automatically  
7. Monthly Nudge effectiveness reports \- This is to understand how much of success is achieved through nudges. The effectiveness is recorded separately in the effectiveness reports. This should be looked at in a month to see if the process is working out

### **Technical**

1. The existing POST /web/message/startFlowForContact endpoint on avni-server will be used.  
   Request Body:  
   `{`   
   `"receiverId": "exampleReceiverId", //user id`  
   `"receiverType": "User", // User or Subject`  
   `"flowId": "exampleFlowId", // Glific flow id`  
   `"parameters": ["param1", "param2"]`  
   `}`  
   Response Body:  
   `{`  
   `"messageDeliveryStatus": "Sent", //Sent / Failed / NotSentNoPhoneNumberInAvni / NotSent`  
   `"errorMessage": null`  
   `}`  
   

`Steps/Process to setup the changes -`  
    
 **1\. Avni DB**

* Inserted external\_system\_config row with system\_name \= 'Glific' (Glific API credentials, base URL, phone, avniSystemUser)  
* Inserted 7 custom queries in custom\_query table  
* Fixed syntax error in "Daily Recording" query (stray semicolon in NOT EXISTS clause)

**2\. Integration Service DB**

* Created integration system entry: name=gdgs26\_27uat, system\_type=rwb  
* Configured integration\_system\_config:  
  * avni\_api\_url — Avni server URL  
  * avni\_user — API user for the org  
  * avni\_password — configured  
  * avni\_auth\_enabled \= true  
  * int\_env \= staging  
  * main.scheduled.job.cron \= 0 0/5 \* \* \* ? (every 5 minutes)

**3\. Service Restart**

* Restarted integration service: sudo systemctl restart avni-int-service\_appserver.service

### **Testing**

2. Rwb-staging will be used for testing. Existing phone numbers used for testing. QA is required for all conditions. Details provided in the sheet

### **Timeline**

- Need to complete integration work by the first week of March, to deliver by the 2nd week of March. 

