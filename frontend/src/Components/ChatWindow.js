import React, {Component, useEffect, useState, usestate, createRef} from 'react';
import Button from 'react-bootstrap/Button'
import Form from 'react-bootstrap/Form';
import {ChatBubble} from './ChatBubble';
import { SendMessage, StartChat } from '../api';

export class ChatWindow extends Component{
  static displayName = ChatWindow.name;
  constructor(props){
    super(props)
    this.state = {messages:[], input:'', chatId:'', awaitingServer: true, textAreaRows: 1}  
    this.fileRef = createRef();
  }

  async componentDidMount(){    
    await this.start();
  }

  sendMessage = async () => {
    if(this.state.input.trim() !== ''){      
      this.setState({messages:[...this.state.messages, {message:this.state.input, userType:'user'}]});      
      this.setState({input:'', messagePending: true});
      const response = await SendMessage(this.state.input, this.state.chatId);
      console.log(response);
      this.setState({messagePending:false});
      this.setState({messages:[...this.state.messages, {message:response.message, userType:'bot'}]})
    }    
  }

  uiDisabled = () =>{
    return this.state.messagePending;
  }

  start = async () => {
    try{
      const response = await StartChat();
      this.setState({messages:[], input:'', chatId: response.chatId, messagePending: false, awaitingServer: false})
    }
    catch(e){
      this.setState({messages:[], input:'', chatId:'', messagePending:false, awaitingServer: true})
      setTimeout(this.start, 5000);
    }    
  }

  handleKeyPress = (event)=>{
    if (event.key === 'Enter') {
      this.sendMessage();
    }
  };

  calculateRows = (text) => {
    const lines = text.split('\n');
    let numLines = 0;
    if(lines){
      lines.forEach(element => {
        numLines += 1
        numLines += element.length / 65      
      });
    }
    
    return Math.min(Math.max(numLines, 1), 5);
  };

  handleTextAreaChange = (e) => {
    const value = e.target.value;
    const rows = this.calculateRows(value);
    this.setState({input:value, textAreaRows: rows});
  } 

  render(){
    if(this.state.awaitingServer){
      return(
        <div style={{display:'flex', justifyContent:'center', alignItems:'center', height: '100vh'}}>
          <span>Awaiting Server to start up...</span>
        </div>
      )
    }   


    return(
    <div style={{display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw', backgroundColor: '#525365'}}>
      <div style={{ flexGrow:1, overflowY: 'auto', padding: '10px', display: 'flex', flexDirection: 'column' }}>
        {this.state.messages.map((msg, index) => (
            <ChatBubble message={msg.message} userType={msg.userType}/>
        ))}
        {
          this.state.messagePending  && 
            (<ChatBubble message="awaiting response" userType="system"/>)          
        }
      </div>

      <Form style={{display: 'flex', justifyContent: 'center', marginBottom: '10px'}}>
        <Form.Group className='mb-2' > 
          <Form.Label style={{color: 'white'}}>Ask a question</Form.Label>          
          <div style={{display: 'flex', width: '100%'}}>
            <Form.Control              
              {... (this.uiDisabled() ? {disabled:'disabled'} : {})}
              onKeyUp={this.handleKeyPress}
              as={'textarea'} 
              rows={this.state.textAreaRows} 
              cols={60}
              value={this.state.input}
              onChange={(e) => this.handleTextAreaChange(e)}
              style={{margin: '5px', flexGrow: 1, maxWidth: '600px', borderRadius: '10px', overflowWrap: 'break-word', overflowX: 'hidden', overflowY: 'auto'}}
              />
            <Button type='submit' style={{height: 'auto', alignSelf: 'stretch'}} disabled={this.uiDisabled() ? 'disabled':''} onClick={this.sendMessage}>Send</Button>
          </div>
          
        </Form.Group>
        
      </Form>
    </div>
    );
  }
}