import React, {Component} from 'react';

export class ChatBubble extends Component{
    render(){
        let backgroundColor;
        let alignSelf;
        switch(this.props.userType){
            case 'user':
                backgroundColor = 'blue';
                alignSelf = 'flex-end';
                break;
            case 'bot':
                backgroundColor = 'green';
                alignSelf = 'flex-start';
                break;
            case 'system':
                backgroundColor = 'lightGray';
                alignSelf = 'flex-start';
                break;
            default:            
                alignSelf = 'flex-start';
        }

        const style = {
            maxWidth: '45%',
            backgroundColor: backgroundColor,
            padding: '8px', 
            margin: '4px', 
            borderRadius: '10px',
            alignSelf: alignSelf
        };
        
        return(
            <div style={style}>
                <span style={{color: 'white', margin: '0px'}}>
                    {this.props.message}
                </span>
            </div>
        )
    }
}
