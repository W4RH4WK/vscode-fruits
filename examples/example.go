package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"os/exec"
	"os/signal"
	"syscall"
)

type Message struct {
	Context string
	Title   string
	Message string
}

func contextToIcon(c string) string {
	switch c {
	case "irc":
		return "/usr/share/icons/Moka/32x32/web/web-irc.png"
	default:
		return ""
	}
}

func handleClient(c net.Conn) {
	defer c.Close()

	d := json.NewDecoder(c)

	var msg Message
	err := d.Decode(&msg)
	if err != nil {
		log.Print("Message Error: ", err)
		return
	}

	log.Printf("Message: %s (%s): %s\n", msg.Title, msg.Context, msg.Message)

	icon := contextToIcon(msg.Context)

	exec.Command("notify-send", "-i", icon, msg.Title, msg.Message).Run()
}

func main() {

	if len(os.Args) < 2 {
		fmt.Printf("Usage: %s <socket-file>\n", os.Args[0])
		return
	}

	l, err := net.Listen("unix", os.Args[1])
	if err != nil {
		log.Fatal("Listen Error: ", err)
	}

	sigc := make(chan os.Signal, 1)
	signal.Notify(sigc, syscall.SIGINT, syscall.SIGTERM)
	go func(c chan os.Signal) {
		sig := <-c
		log.Printf("Caught signal %s, shutting down", sig)
		l.Close()
		os.Exit(0)
	}(sigc)

	for {
		conn, err := l.Accept()
		if err != nil {
			log.Print("Accept Error: ", err)
		}
		go handleClient(conn)
	}
}
