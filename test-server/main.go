package main

import (
	"bufio"
	"github.com/tony84727/minebrain/test-server/forgegrpc"
	"google.golang.org/grpc"
	"log"
	"net"
	"os"
)

type serverImpl struct {}

func (s serverImpl) Connect(connectServer forgegrpc.Chat_ConnectServer) error {
	reader := bufio.NewReader(os.Stdin)
	for {
		line, err :=reader.ReadString('\n')
		if err != nil {
			return err
		}
		if err := connectServer.Send(&forgegrpc.ChatEvent{Content: line, Sender: "test-server"}); err != nil {
			return err
		}
	}
}

func main() {
	server := grpc.NewServer()
	forgegrpc.RegisterChatServer(server, serverImpl{})
	listener, err := net.Listen("tcp4","localhost:30000")
	if err != nil {
		log.Fatal(err)
	}
	if err := server.Serve(listener); err != nil {
		log.Fatal(err)
	}
	//quit := make(chan os.Signal)
	//signal.Notify(quit, os.Interrupt)
	//<- quit
}
