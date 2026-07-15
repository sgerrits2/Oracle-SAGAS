# Lab 1: Distributed Transactions and the Power of Sagas

## Introduction

Modern software systems are increasingly composed of distributed microservices that operate across multiple databases, modules, and services. Ensuring **data consistency** in such distributed systems is a significant challenge, especially when operations span multiple steps or services that can fail independently.

Traditional approaches such as **Two-Phase Commit (2PC)** are often heavyweight and difficult to scale, particularly for long-running transactions. Moreover, they can introduce performance bottlenecks due to locks and tight coordination across services. To address these issues, the **Saga Pattern** has emerged as a reliable alternative for managing **long-running, distributed transactions (LRA)**.

In this lab, we will explore the foundations of distributed transaction challenges, the Saga pattern, and how **Oracle Sagas** makes implementing this pattern seamless inside the Oracle Database. We’ll use the **CloudBank application** as our running example throughout the lab. This application models a simple yet realistic scenario: transferring money from one bank to another, such as from **Bank A to Bank B**, involving account debit, credit, validations, and failure handling.

* Estimated Time: XX minutes

### Objectives

By the end of this lab, you will be able to:

- Describe the challenges of distributed transactions in microservice-based systems.
- Explain the core concepts of the **Saga Pattern** and how it ensures eventual consistency.
- Explore how Oracle Sagas can simplify multi-step distributed workflows.

### Prerequisites
Before starting this lab, ensure you have:
- A Free-Tier or Livelabs Oracle Cloud account.
- Basic understanding of **PL/SQL** or **Java** programming.


## Task 1: Understanding Challenges of Distributed Transactions

---

Let us imagine the **CloudBank** application. A user initiates a transfer from **Bank A** to **Bank B**. Internally, the system must:

1. Validate that the source account has enough balance.
2. Debit the amount from Bank A's account.
3. Send a message or trigger to Bank B.
4. Credit the destination account in Bank B.
5. Log the transaction and notify the user.

If any of these steps fail—say, the network between Bank A and Bank B goes down, or the credit to Bank B fails—**data inconsistency** could arise. For example, the amount could be debited from Bank A, but not credited to Bank B.

Traditional 2PC would try to lock both databases until the entire operation completes. This can:

- **Block resources for a long time**.
- **Impact system availability**.
- **Not scale well with multiple concurrent transfers**.

**Real-World Pain Points**:

- **Network partitions** between services.
- **Partial failures** that are hard to rollback.
- **Inconsistent recovery logic** coded across different teams.
- **Retry storms**, where multiple retries lead to duplicate or invalid operations.

Hence, a better strategy is to model the transaction as a **Saga**, where each step is independent and can be compensated if needed.

---

## Task 2: Concepts of the Saga Pattern

---

The **Saga Pattern** is a sequence of **local transactions**, where each transaction updates data within a single service, and subsequent steps are triggered by messaging. The **Saga Pattern** is an architectural pattern that breaks a long-running transaction into a sequence of **smaller, independent sub-transactions**, each with a corresponding **compensating transaction**. If one of the steps fails, a series of **compensating transactions** are executed to undo the previous operations.

### Key Concepts:

- **Local Transactions:** Each step is a local transaction that commits independently.
- **Compensation:** Reverses effects of already-committed transactions on failure.
- **Forward Recovery:** Instead of rolling back, sagas aim to restore consistency by applying business-defined compensations.

### Characteristics of Saga:

- **Asynchronous coordination**
- **No global locks**
- **Eventually consistent**
- **Scalable and fault-tolerant**

---

## Task 3: Oracle Sagas to the Rescue

---

Oracle Sagas provide **native support for the Saga Pattern** in Oracle Database 23ai onwards. The feature enables developers to model, execute, monitor, and compensate multi-step workflows directly within the database engine using either **PL/SQL** or **Java**. Implementing the Saga pattern manually requires:

- Messaging infrastructure
- State management
- Compensation logic
- Consistent transaction flow orchestration

This can become error-prone and time-consuming.

Oracle Sagas allows developers to:

- Register participants, coordinators, and initiators using the `DBMS_SAGA_ADM` package.
- Define compensation logic in PL/SQL or Java
- Use declarative annotations in Java or the API's `DBMS_SAGA` PL/SQL package
- Monitor saga execution states
- Scale across PDBs or databases using TEQ and DB Links

### Key Features:

- **`DBMS_SAGA`** package for PL/SQL development.
- **Oracle Sagas Java Client** for Java-based orchestration using annotations.
- **Saga Broker, Coordinator, and Participants** as runtime components using the **`DBMS_SAGA_ADM`** package.
- **Automatic Compensation Execution** based on failure conditions.
- **Real-Time Monitoring** using dictionary views and logs.

### Benefits:

- Unified development using existing database tools
- Faster time to implementation
- Built-in reliability
- Auditing and traceability with system views and logs

![Oracle Saga Benefits](./images/l1-t3-1.png "Oracle Saga Benefits")

Going back to our CloudBank scenario, developers only need to:

- Register the Initiator and Participants
- Define the saga workflows
- Add optional compensating procedures
- Use simple APIs or annotations to trigger the Saga

Manually building this infrastructure is not just slow—it’s painful, and often fragile. Oracle Sagas are tightly integrated with the Oracle database, enabling rollback-safe distributed transactions with minimal overhead.

---

## Task 4: Real-World Examples and Scenarios

---

Oracle Sagas can be applied to a variety of real-world distributed business processes. The following are some common scenarios:

### 1. **Banking and Financial Services**

- Funds transfer involving multiple institutions
- Loan approval workflows with intermediate checks

### 2. **E-commerce Order Processing**

- Inventory check → payment processing → shipment scheduling
- Refund processing with partial shipment

### 3. **Supply Chain Management**

- Multi-vendor procurement workflows
- Reversal of reserved stock across distributed warehouses

### 4. **Healthcare Scheduling**

- Schedule doctor → reserve lab → confirm pharmacy
- If lab slot unavailable, rollback appointment

### 5. **Insurance Claims**

- Multi-step claim approval with cross-departmental validation
- Claim rejection requiring compensations for already-processed steps

### Additional Use Cases:
- Telecom service provisioning
- IoT device onboarding
- Banking KYC and onboarding
- Travel planning
- and many more ...

In each case, Oracle Sagas gives you a flexible, native, and resilient way to model all of these without external tools or heavyweight locking mechanisms.
In real-world teams, workflows rarely go as planned. Oracle Sagas let you build systems that expect the unexpected—and recover gracefully.

---

## Task 5: Optional Quiz — Test Your Understanding

---

This short interactive quiz is designed to reinforce your understanding of Sagas, their benefits, and real-world use cases. You'll be presented with a couple of conceptual questions based on the material covered so far. Use this opportunity to validate your learning before moving to the environment setup in the next lab.

<style>
.card-1 {
  background-color: #fff;
  box-shadow: 0 5px 10px rgba(0,0,0,0.5);
  padding: 20px;
  border-radius: 8px;
  width: 80%;
  max-width: 900px;
  margin: 40px auto;
  text-align: center;
  min-height: 350px; 
}

#quiz-start{
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 350px; 
  position: relative;
  z-index: 1;
}

#quiz-finish {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  max-width: 100%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0 10px;
  z-index: 1;
}

#options-list li.selected {
  background-color: #e0e0e0;
}
#options-list li.correct {
  background-color: #d4edda;
  border-color: #c3e6cb;
  color: #155724;
}
#options-list li.wrong {
  background-color: #f8d7da;
  border-color: #f5c6cb;
  color: #721c24;
}
#options-list li.disabled {
  opacity: 0.6;
  pointer-events: none;
}

#button-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
}

.submit-btn {
  background-color: #ae562c;
  color: #fff;
  padding: 12px 24px;
  font-size: 18px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  align-items: center;
}
.nav-btn {
  background-color: #ae562c;
  color: #fff;
  padding: 10px 0;
  width: 120px;
  font-size: 16px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  text-align: center;
  box-sizing: border-box;
}

.nav-btn:disabled,
.submit-btn:disabled {
  background-color: #ccc;    
  color:            #777;    
  cursor:           not-allowed;
  pointer-events:   none;    
  opacity:          0.8;     
}

#navigation-row {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin-top: 15px;
}

#quiz-finish h3 {
  animation: pop-in 1.5s ease-out;
  max-width: 90%;
  word-break: break-word;
  text-align: center;
  font-size: 1.5em;
  line-height: 1.2;
  margin: 0 auto;
}

@keyframes pop-in {
  0% { transform: scale(0.5); opacity: 0; }
  100% { transform: scale(1); opacity: 1; }
}

@media (max-width: 480px) {
  #quiz-finish h3 {
    font-size: 1.2em;
  }
}
</style>

<div id="quiz-wrapper" style="display: flex; justify-content: center;">
<div id="quiz-card" class="card-1" style="position: relative; overflow: hidden; max-width: none !important;">
<canvas id="confetti-canvas" style="display: none; position: absolute; top: 0; left: 0; width: 100% !important; height: 100% !important; pointer-events: none; z-index: 0; background-color: transparent; max-width: none !important;"></canvas>
  <div id="quiz-start">
    <h3>Ready to Test?</h3>
    <button id="start-btn" style="background-color:#ae562c; color:#fff; padding:10px 20px; border:none; border-radius:5px; font-size:16px; cursor:pointer;">Start Quiz</button>
  </div>
  <div id="quiz-content" style="display:none;   position: relative; z-index: 1;">
    <div id="question-area">
  <h4 id="question-number" style="margin-bottom: 0;"></h4>
  <div id="question-text" style="margin-top: 5px;"></div>
  <ul id="options-list" style="list-style:none; padding:0;"></ul>
</div>


<div id="button-row" style="display: flex; justify-content: center; margin-top: 20px;">
  <button id="submit-btn" class="submit-btn">Submit</button>
</div>
<div id="navigation-row">
  <button id="prev-btn" class="nav-btn">← Previous</button>
  <button id="next-btn" class="nav-btn">Next →</button>
</div>

  </div>
<div id="quiz-finish" style="display:none;">
<h3 style="font-size: 28px; font-weight: bold;">🎉 Great Job! Let's move on to the next lab. 🎉</h3>
</div>
</div>
</div>

<script src="https://cdn.jsdelivr.net/npm/canvas-confetti@1.6.0/dist/confetti.browser.min.js"></script>

<script>
const questions = [
  {
    question: "If you were designing a banking app like CloudBank, what would be your biggest concern for multi-step money transfers?",
    options: [
      "How to make the UI look better",
      "Ensuring all steps are consistent even if one fails",
      "Getting the color theme right",
      "Increasing the number of logs"
    ],
    correct: 1
  },
  {
    question: "Why would a developer prefer Saga over traditional 2PC for distributed transactions?",
    options: [
      "Sagas use fancy locks",
      "Sagas avoid long-held locks and allow easier scaling",
      "2PC is too simple",
      "Sagas are always synchronous"
    ],
    correct: 1
  },
  {
    question: "Imagine a failure while crediting Bank B in CloudBank. What should ideally happen?",
    options: [
      "Ignore the error",
      "Restart the whole database",
      "Refund the amount by compensating the debit from Bank A",
      "Ask user to retry after some time"
    ],
    correct: 2
  },
  {
    question: "Which component in Oracle Sagas ensures all steps coordinate and messages are routed?",
    options: [
      "DBMS_SCHEDULER",
      "Oracle Streams",
      "Saga Coordinator and Broker",
      "Data Guard"
    ],
    correct: 2
  },
  {
    question: "Which one best describes a compensating transaction?",
    options: [
      "It makes sure the saga runs faster",
      "It handles errors silently",
      "It undoes the effect of a previously successful step",
      "It optimizes the transaction path"
    ],
    correct: 2
  },
  {
    question: "How does Oracle make it easier to implement Sagas?",
    options: [
      "By offering a visual dashboard only",
      "Through native support via PL/SQL and Java APIs",
      "Using cloud-only APIs",
      "With additional licensing"
    ],
    correct: 1
  },
  {
    question: "What’s a benefit of using Oracle’s native Saga support?",
    options: [
      "You need fewer databases",
      "No need to manually handle retries and compensation flows",
      "You can only use Java, not PL/SQL",
      "All transactions are fully rolled back by default"
    ],
    correct: 1
  },
  {
    question: "If you had to write Saga logic in Oracle, which tools would you likely use?",
    options: [
      "Only REST APIs",
      "Manual shell scripts",
      "DBMS_SAGA or Java Annotations",
      "Triggers and views"
    ],
    correct: 2
  },
  {
    question: "What is the typical flow of a Saga transaction?",
    options: [
      "All steps in parallel",
      "Single long-running lock-based operation",
      "Step-by-step operations with compensation for failures",
      "One global commit at the end"
    ],
    correct: 2
  },
  {
    question: "What does the Initiator in a Saga do?",
    options: [
      "Starts and tracks the overall saga flow",
      "Performs compensation logic",
      "Logs audit entries only",
      "Acts as the Broker"
    ],
    correct: 0
  },
  {
    question: "Which language(s) can be used to define Saga compensation logic in Oracle?",
    options: [
      "Only Java",
      "Only SQL*Plus",
      "Java and PL/SQL",
      "Python only"
    ],
    correct: 2
  },
  {
    question: "Which of these best describes the nature of Saga transactions?",
    options: [
      "Eventually consistent and failure-resilient",
      "Immediate and always atomic",
      "Undo-free and lock-heavy",
      "Parallel and lock-based"
    ],
    correct: 0
  },
  {
    question: "Which Oracle component helps with message delivery and saga coordination?",
    options: [
      "DBMS_OUTPUT",
      "Oracle AQ (Advanced Queuing)",
      "SQL Loader",
      "Oracle Scheduler"
    ],
    correct: 1
  },
  {
    question: "If you had to convince your team to use Sagas, what would be a compelling reason?",
    options: [
      "We can scale workflows across services easily",
      "We can avoid writing any compensation logic",
      "We can lock fewer tables with 2PC",
      "It’s faster than a SELECT query"
    ],
    correct: 0
  },
  {
  question: "If you were building a money transfer service between banks, which concern would likely top your list?",
  options: [
    "Having fancy fonts on the UI",
    "Making sure funds aren't lost midway",
    "Limiting database users",
    "Caching all data globally"
  ],
  correct: 1
},
{
  question: "If your team asked you how to handle failures in multi-step workflows, what would you suggest?",
  options: [
    "Use Sagas with compensating transactions",
    "Let the DBA fix it manually",
    "Ignore and log the error",
    "Force retry every failed step until it succeeds"
  ],
  correct: 0
},
{
  question: "If a customer complains that money got debited but not credited, what could have gone wrong?",
  options: [
    "The UI didn't refresh",
    "The Saga compensation wasn't configured properly",
    "Database views were not refreshed",
    "Customer's phone crashed"
  ],
  correct: 1
},
{
  question: "If your app performs a debit and credit across two databases and the credit fails, what would you want to do?",
  options: [
    "Wait and hope the credit goes through later",
    "Compensate the debit with a refund using a Saga",
    "Log the issue and move on",
    "Disable the credit step"
  ],
  correct: 1
},
{
  question: "If you were debugging a Saga failure, what would help you the most?",
  options: [
    "Saga monitoring views",
    "Java stack traces only",
    "V$SESSION_LONGOPS",
    "A printout of all packages"
  ],
  correct: 0
},
{
  question: "If your application spans multiple services and databases, why might you choose Oracle Sagas?",
  options: [
    "Because it replaces triggers",
    "To simplify coordination and rollback across services",
    "To increase PL/SQL syntax errors",
    "To disable transactions"
  ],
  correct: 1
},
{
  question: "If you had to explain Oracle Saga's benefit in one sentence to a colleague, what would you likely say?",
  options: [
    "It automates scaling of your compute units",
    "It ensures multi-step transactions are safe and reversible",
    "It turns everything into a 2PC transaction",
    "It removes the need for SQL entirely"
  ],
  correct: 1
},
{
  question: "If your project required money transfer with high availability and no locking, which technique would best fit?",
  options: [
    "Saga pattern using Oracle’s native features",
    "Using Excel macros to simulate flows",
    "Single-node commits",
    "Disabling all constraints"
  ],
  correct: 0
},
{
  question: "If you wanted each step of a transaction to be independent but recoverable, what would you use?",
  options: [
    "Saga steps with compensating actions",
    "2PC with nested transactions",
    "Rollback segments",
    "Materialized views"
  ],
  correct: 0
},
{
  question: "If you were told to write your own Saga framework from scratch, what might be a concern?",
  options: [
    "Managing UI buttons",
    "Maintaining consistent state and retries",
    "Choosing the best font",
    "Avoiding PL/SQL entirely"
  ],
  correct: 1
}
];

let selectedQuestions = [];
let userSelections = [];
let submissions = [];
let wrongSelections = [];
let currentQ = 0;

function shuffleArr(array) {
  let m = array.length, t, i;
  while (m) {
    i = Math.floor(Math.random() * m--);
    t = array[m];
    array[m] = array[i];
    array[i] = t;
  }
  return array;
}

document.getElementById("start-btn").onclick = function(){
  selectedQuestions = shuffleArr([...questions]).slice(0,5);
  userSelections = Array(5).fill(null);
  submissions = Array(5).fill(false);
  wrongSelections = Array(5).fill().map(() => []);
  currentQ = 0;
  document.getElementById("quiz-start").style.display = "none";
  document.getElementById("quiz-content").style.display = "block";
  renderQ();
};

function renderQ(){
  const q = selectedQuestions[currentQ];
  document.getElementById("question-number").innerText = 'Question ' + (currentQ + 1);
  document.getElementById("question-text").innerText = q.question;
  const optionsList = document.getElementById("options-list");
  optionsList.innerHTML = "";
  q.options.forEach((opt, idx) => {
    const li = document.createElement("li");
    li.innerText = opt;
    li.style.padding = "10px";
    li.style.border = "1px solid #ccc";
    li.style.borderRadius = "5px";
    li.style.margin = "8px 0";
    li.style.cursor = "pointer";
    if(submissions[currentQ]){
      if(idx === q.correct){ li.classList.add("correct"); li.style.backgroundColor = "#d4edda"; li.style.borderColor = "#c3e6cb"; li.style.color = "#155724"; }
      else{ li.classList.add("disabled"); li.style.opacity = "0.6"; li.style.cursor = "not-allowed"; }
    }else{
      if(wrongSelections[currentQ].includes(idx)){ li.classList.add("wrong"); li.style.backgroundColor = "#f8d7da"; li.style.borderColor = "#f5c6cb"; li.style.color = "#721c24"; li.style.cursor = "not-allowed"; }
      else if(userSelections[currentQ] === idx){ li.classList.add("selected"); li.style.backgroundColor = "#e0e0e0"; }
    }
    li.onclick = () => {
      if(submissions[currentQ] || wrongSelections[currentQ].includes(idx)) return;
      userSelections[currentQ] = idx;
      renderQ();
    };
    optionsList.appendChild(li);
  });
  const submitBtn = document.getElementById("submit-btn");
  submitBtn.disabled = submissions[currentQ] || userSelections[currentQ] === null;
  submitBtn.style.cursor = submitBtn.disabled ? "not-allowed" : "pointer";
  const nextBtn = document.getElementById("next-btn");
  nextBtn.disabled = !submissions[currentQ];
  nextBtn.style.cursor = nextBtn.disabled ? "not-allowed" : "pointer";
  nextBtn.style.display = currentQ < 4 ? "inline-block" : "none";
  const prevBtn = document.getElementById("prev-btn");
  prevBtn.disabled = currentQ === 0;
  prevBtn.style.cursor = prevBtn.disabled ? "not-allowed" : "pointer";
}

document.getElementById("submit-btn").onclick = function () {
  if (submissions[currentQ] || userSelections[currentQ] === null) return;
  const q = selectedQuestions[currentQ];
  const chosen = userSelections[currentQ];
  if (chosen === q.correct) {
    submissions[currentQ] = true;
    if (submissions.every((submitted, idx) => submitted && userSelections[idx] === selectedQuestions[idx].correct)) {
  document.getElementById("quiz-content").style.display = "none";
  document.getElementById("quiz-finish").style.display = "block";
  runConfetti();
}
  } else {
    wrongSelections[currentQ].push(chosen);
    userSelections[currentQ] = null;
  }
  renderQ();
};

document.getElementById("next-btn").onclick = function(){
  if(submissions[currentQ] && currentQ < 4){
    currentQ++;
    renderQ();
  }
};

document.getElementById("prev-btn").onclick = function(){
  if(currentQ > 0){
    currentQ--;
    renderQ();
  }
};

let confettiInterval;

function runConfetti() {
  const canvas = document.getElementById("confetti-canvas");
  canvas.style.display = "block";

  const myConfetti = confetti.create(canvas, { resize: true, useWorker: true });

  clearInterval(confettiInterval);

  confettiInterval = setInterval(() => {
    myConfetti({
      particleCount: 100,
      spread: 80,
      origin: { x: Math.random(), y: Math.random() - 0.2}
    });
  }, 600);
}

</script>

Now that you’ve seen how sagas can solve messy coordination problems—imagine having this power baked right into your database. Let’s get your environment ready.

You may now [proceed to the next lab](#next)

## Learn More

- For an overview of developing applications with Sagas, see the [Oracle Database 23ai Sagas Documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/adfns/developing-applications-saga.html).

- For technical details on PL/SQL support, refer to the [`DBMS_SAGA` Package Reference](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga.html) and [`DBMS_SAGA_ADM`](https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/dbms_saga_adm.html).

- For integrating Sagas with Java, see the [Oracle Sagas Java Client Maven Repository](https://mvnrepository.com/artifact/com.oracle.database.saga/saga-core/23.7.0) and official API documentation.

## Acknowledgements

- **Contributors** - Amit Ketkar, Pavas Navaney, Vinay Pandhariwal,
Luis Cruz, Sebastian Gerritsen 
- **Created By/Date** - Vinay Pandhariwal, August 2025
- **Last Updated By/Date** - Vinay Pandhariwal, August 2025
