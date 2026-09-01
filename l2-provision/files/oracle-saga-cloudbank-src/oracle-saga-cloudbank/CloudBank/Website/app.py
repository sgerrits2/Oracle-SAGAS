# -----------------------------------------------------------------------------
# Copyright (c) 2025, Oracle and/or its affiliates.
#
# This software is dual-licensed to you under the Universal Permissive License
# (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl and Apache License
# 2.0 as shown at http://www.apache.org/licenses/LICENSE-2.0. You may choose
# either license.
#
# If you elect to accept the software under the Apache License, Version 2.0,
# the following applies:
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# -----------------------------------------------------------------------------

import eventlet
eventlet.monkey_patch()

from flask import Flask, request, session, redirect, url_for, render_template, jsonify, current_app
from flask_restx import Api, Resource, fields
from flask_cors import CORS
from flask_socketio import SocketIO, emit, join_room, leave_room
import requests
import json
from datetime import datetime
import threading
import time
from collections import defaultdict



app = Flask(__name__)
app.secret_key = 'oracle'
# SOCKET IS USED FOR NOTIFICATION SYSTEM ON THE WEBSITE
# socketio = SocketIO(app)
socketio = SocketIO(app, async_mode="eventlet") 


CORS(app, resources={r"/swagger.json": {"origins": "*"}})

# Endpoint to serve the index.html page
@app.route('/')
def index():
    return render_template('index.html')

api = Api(
  app,
  version='1.0',
  title='CLOUDBANK UI',
  description='Endpoints for CLOUDBANK UI',
  doc='/docs'
)



# URL's FOR THE CLOUDBANK ENDPOINTS.
URL_CLOUDBANK_LOGIN = "http://orchestrator:8081/orchestrator/login"
URL_CLOUDBANK_NEW_CUSTOMER = "http://orchestrator:8081/orchestrator/newCustomer"
URL_CLOUDBANK_NEW_BANK_ACCOUNT = "http://orchestrator:8081/orchestrator/newBankAccount"
URL_CLOUDBANK_TRANSFER = "http://orchestrator:8081/orchestrator/transfer"
URL_CLOUDBANK_REFRESH = "http://orchestrator:8081/orchestrator/refresh"
URL_CLOUDBANK_NOTIFICATION = "http://orchestrator:8081/orchestrator/notification"
URL_CLOUDBANK_LOGS = "http://orchestrator:8081/orchestrator/logs"
URL_BANK_CHICAGO_LOGS = "http://banka:8082/banka/logs"
URL_BANK_MEX_LOGS = "http://bankb:8083/bankb/logs"
HTTP_TIMEOUT = (3.05, 15)


# Endpoint to serve the login.html page
@app.route('/login', methods=['GET','POST'])
def login():
    if request.method == 'POST':
        username = request.form['username']
        password = request.form['password']
    
        
        # Making the API call
        response = requests.post(URL_CLOUDBANK_LOGIN, json={'id': username, 'pwd': password}, timeout=HTTP_TIMEOUT)
        
        if response.status_code == requests.codes.accepted:
            # Parse the JSON returned by the API
            data_main = response.json()
            data = json.loads(data_main['data'])
            
            for key, value in data.items():
                if isinstance(value, list):
                    session[key] = json.dumps(value)
                else:
                    session[key] = value
            
            return redirect(url_for('dashboard'))
        else:
            # Handle login failure
            error = "Login failed. Please check your credentials."
            return render_template('login.html', error=error)
    else:
        login_id = session.pop('login_id', None)
        return render_template('login.html', login_id=login_id)

# Endpoint to serve the dashboard.html page
@app.route('/dashboard', methods=['GET'])
def dashboard():
    if 'fullName' not in session:  
        return redirect(url_for('login'))
    
    saga_id = session.pop("new_bank_saga_id", None)
    reason = session.pop("new_bank_reason", None)
    
    
    user_data = {}
    for key, value in session.items():
        # Check if the value is a JSON string that needs to be parsed
        if key in ['CHECKING', 'SAVING']:
            try:
                user_data[key] = json.loads(value)
            except json.JSONDecodeError:
                user_data[key] = []
        else:
            user_data[key] = value

    return render_template('dashboard.html', user_data=user_data, datetime=datetime, saga_id=saga_id, reason=reason)

# Endpoint to serve the refresh button on dashboard
@app.route('/refresh-dashboard', methods=['POST'])
def refresh_dashboard():
    
    response = requests.post(URL_CLOUDBANK_REFRESH, json={'ucid': session.get('ucid'), 'ossn': session.get('ossn')}, timeout=HTTP_TIMEOUT)
            
    data_main = response.json()
    data = json.loads(data_main['data'])
            
    for key, value in data.items():
        if isinstance(value, list):
            session[key] = json.dumps(value)
        else:
            session[key] = value
            

    return redirect(url_for('dashboard'))
    
# Endpoint to serve the createNewAccount.html page
@app.route('/createNewAccount', methods=['GET', 'POST'])
def create_new_account():
    if request.method == 'POST':
        # Extract data from form
        fullname = request.form.get('fullname')
        address = request.form.get('address') 
        phone = request.form.get('phone')
        email = request.form.get('email')
        ossn = request.form.get('ossn')
        bank = request.form.get('bank')
        password = request.form.get('password')

        # Make the API call
        
        response = requests.post(URL_CLOUDBANK_NEW_CUSTOMER, json={
            'fullName': fullname,
            'address': address,
            'phone': phone,
            'email': email,
            'ossn': ossn,
            'password': password,
            "bank": bank
        }, timeout=HTTP_TIMEOUT)

        if response.status_code == requests.codes.accepted: 
            response_data = response.json()
            login_id = response_data.get('login_id')
            session['login_id'] = login_id
            return redirect(url_for('login'))
        else:
            error = response.json().get('reason')
            return render_template('createNewAccount.html', error=error)
    else:
        return render_template('createNewAccount.html')

# Endpoint to serve the createBankAccount.html page
@app.route('/create_bank_account', methods=['GET', 'POST'])
def create_bank_account():
    if 'fullName' not in session:  
        return redirect(url_for('login'))
    
    user_data = {}
    for key, value in session.items():
        # Check if the value is a JSON string that needs to be parsed
        if key in ['CHECKING', 'SAVING']:
            try:
                user_data[key] = json.loads(value)
            except json.JSONDecodeError:
                user_data[key] = []
        else:
            user_data[key] = value
    
    if request.method == 'POST':
        account_type = request.form.get('account_type')
        if account_type == 'BANK_ACCOUNT':
            sub_type = request.form.get('sub_type')  
            response = requests.post(URL_CLOUDBANK_NEW_BANK_ACCOUNT, json={'operationType': 'NEW_BANK_ACCOUNT', 'ucid': session.get("ucid"), 'accountType': sub_type}, timeout=HTTP_TIMEOUT)

        if response.status_code == requests.codes.accepted: 
            response_data = response.json()
            reason = response_data.get('reason')
            saga_id = response_data.get('id')
            session['new_bank_saga_id'] = saga_id
            session['new_bank_reason'] = reason
            return redirect(url_for('dashboard')) 
        else:
            error = 'Unable to create new account'
            return render_template('newBankAccount.html', error=error)
    else:
        return render_template('newBankAccount.html', user_data=user_data)

# Endpoint to serve the transfer.html page
@app.route('/transfer', methods=['GET','POST'])
def transfer():
    if 'fullName' not in session:  
        return redirect(url_for('login'))
    
    user_data = {}
    for key, value in session.items():
        # Check if the value is a JSON string that needs to be parsed
        if key in ['CHECKING', 'SAVING']:
            try:
                user_data[key] = json.loads(value)
            except json.JSONDecodeError:
                user_data[key] = []
        else:
            user_data[key] = value
    
    if request.method == 'POST':
    
        from_account = request.form.get('from_account')
        to_account = request.form.get('to_account')
        amount = request.form.get('amount')
        password = request.form.get('password')
    
        response = requests.post(URL_CLOUDBANK_TRANSFER, json={'ucid': session.get("ucid"), 'toAccountNumber': to_account, 'fromAccountNumber': from_account, 'amount': amount, 'password': password}, timeout=HTTP_TIMEOUT)

        if response.status_code == requests.codes.accepted: 
            response_data = response.json()
            reason = response_data.get('reason')
            saga_id = response_data.get('id')
            session['new_bank_saga_id'] = saga_id
            session['new_bank_reason'] = reason
            return redirect(url_for('dashboard')) 
        else:
            error = 'Unable to initiate transfer. Please check all the details and try again'
            return render_template('transfer.html', error=error, user_data=user_data)
    else:
        return render_template('transfer.html', user_data=user_data)
    

# Endpoint to serve the account_details.html page
@app.route('/account-details')
def account_details():
    if 'fullName' not in session:  
        return redirect(url_for('login'))
    
    user_data = {
        'fullName': session.get('fullName'),
        'email': session.get('email'),
        'phone': session.get('phone'),
        'address': session.get('address'),
        'ossn': session.get('ossn'),  
        'ucid': session.get('ucid'),
        'bank': session.get('bank')
    }
    
    # Render the account details page with the user details
    return render_template('account_details.html', user_data=user_data)
    
 # Endpoint to serve the logout / session close functionality.
@app.route('/logout')
def logout():
    session.clear()  
    return redirect(url_for('login'))

# Notification Logic

@socketio.on('connect')
def handle_connect():
    ucid = get_user_id_from_request()
    if ucid:
        # session_id = request.sid
        # user_sessions[ucid] = session_id
        # print(user_sessions)
        join_room(ucid)
        print(f'User {ucid} connected with SID {request.sid}')

@socketio.on('disconnect')
def handle_disconnect():
    ucid = get_user_id_from_request()
    if ucid:
        leave_room(ucid)
        print(f'User {ucid} disconnected from SID {request.sid}')

        
def get_user_id_from_request():
    return session.get('ucid')
                
def fetch_new_entries():
    try:
        response = requests.get(URL_CLOUDBANK_NOTIFICATION, timeout=HTTP_TIMEOUT)
        if response.status_code == requests.codes.accepted:
            response_json = response.json()
            
            data_str = response_json.get('data')
            if data_str:
                data_list = json.loads(data_str)
                new_entries = [json.loads(entry) for entry in data_list]
                
                return new_entries
        else:
            print(f"Failed to fetch new entries. Status code: {response.status_code}")
            return []
    except requests.RequestException as e:
        print(f"An error occurred: {e}")
        return []
    except json.JSONDecodeError as e:
        print(f"JSON decode error: {e}")
        return []

notification_cycles = defaultdict(int)

def notify_new_entries():
    while True:
        fetched_entries = fetch_new_entries()
        for entry in fetched_entries:
            key = (entry['sagaId'], entry['ucid'], entry['operationType'], entry['operationStatus'])
            if key not in notification_cycles:
                notification_cycles[key] = 0
        keys_to_remove = [] 
        for key, count in notification_cycles.items():
            saga_id, ucid, operationType, operation_status = key
            user_id = ucid  
            data = 'Request ID: ' + saga_id + '. The ' + operationType + ' operation\'s status is: ' + operation_status
            print(data)
            print(user_id)
            socketio.emit('new_notification', {'message': data}, room=user_id)
            notification_cycles[key] += 1
            if notification_cycles[key] >= 1:
                keys_to_remove.append(key)
        
        for key in keys_to_remove:
            del notification_cycles[key]
        time.sleep(15)

@app.route('/cloudbank-logs')
def cloudbank_logs():
    ucid = session.get('ucid')
    if not ucid:
        abort(401, description="not authenticated")

    try:
        resp = requests.get(URL_CLOUDBANK_LOGS, params={'ucid': ucid}, timeout=HTTP_TIMEOUT)
        resp.raise_for_status()
        payload = resp.json()
        return jsonify({ 'data': payload })
    except requests.RequestException as e:
        current_app.logger.exception("Error fetching CloudBank logs")
        return jsonify({ 'error': str(e) }), 500


@app.route('/participant-logs')
def participant_logs():
    ucid = session.get('ucid')
    if not ucid:
        abort(401, description="not authenticated")
    
    accounts = []
    for bucket in ("CHECKING","SAVING"):
        raw = session.get(bucket, "[]")
        try:
            bucket_list = json.loads(raw)
        except ValueError:
            bucket_list = []
        accounts += [acct["account_number"] for acct in bucket_list]

    bank = session.get('bank', '').lower()
    target_url = URL_BANK_CHICAGO_LOGS if bank == 'bankchicago' else URL_BANK_MEX_LOGS

    try:
        resp = requests.get(target_url, params={'ucid': ucid,
                               "accountNumbers":  accounts}, timeout=HTTP_TIMEOUT)
        resp.raise_for_status()
        payload = resp.json()
        return jsonify({ 'data': payload })
    except requests.RequestException as e:
        current_app.logger.exception("Error fetching participant logs")
        return jsonify({ 'error': str(e) }), 500
        

if __name__ == '__main__':
    # app.run(host="0.0.0.0", port=8084, debug=True)
    socketio.start_background_task(notify_new_entries)
    socketio.run(app, host="0.0.0.0", port=8084)
