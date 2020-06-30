package main

import (
	"encoding/binary"
	"encoding/json"
	"flag"
	"io"
	"log"
	"math/rand"
	"net"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/dustin/go-broadcast"
	"github.com/gorilla/websocket"
)

var tcpToWsBroadcast = broadcast.NewBroadcaster(100)
var wsToTcpBroadcast = broadcast.NewBroadcaster(100)

// ----------------------------------------------------------------------- Main

func main() {
	tcpPort := flag.Int("tcp-port", 1337, "port for the TCP listener")
	httpPort := flag.Int("http-port", 8080, "port for the HTTP listener")
	msgGen := flag.Bool("msg-gen", false, "generate random messages")
	msgGenIv := flag.Int("msg-gen-interval", 1000, "message generator interval [ms]")
	flag.Parse()

	if *msgGen {
		interval := time.Duration(*msgGenIv) * time.Millisecond
		go messageGenerator(interval)
	}

	go listenAndServeTCP(*tcpPort)
	listenAndServeHTTP(*httpPort)
}

// ----------------------------------------------------------------- TCP Server

func listenAndServeTCP(port int) {
	log.Println("Starting TCP listener on", port)

	l, err := net.Listen("tcp", ":"+strconv.Itoa(port))
	if err != nil {
		log.Panicln(err)
	}
	defer l.Close()

	for {
		conn, err := l.Accept()
		if err != nil {
			log.Panicln(err)
		}

		go handleTCPRequest(conn)
	}
}

func handleTCPRequest(conn net.Conn) {
	log.Println("TCP: Accepted new connection")
	defer conn.Close()
	defer log.Println("TCP: Closing connection")

	mrw := newMessageReadWriter(conn)

	// cleanup will be done by handleTCPRequestRead
	wsToTcpChannel := make(chan interface{})
	wsToTcpBroadcast.Register(wsToTcpChannel)

	var wg sync.WaitGroup
	wg.Add(2)

	go handleTCPRequestRead(&wg, wsToTcpChannel, mrw)
	go handleTCPRequestWrite(&wg, wsToTcpChannel, mrw)

	wg.Wait()
}

func handleTCPRequestRead(wg *sync.WaitGroup, wsToTcpChannel chan interface{}, mrw *messageReadWriter) {
	defer wg.Done()

	for {
		msg, err := mrw.ReadMessage()
		if err != nil {
			break
		}

		log.Println("TCP:", mrw, ":", "successfully read message")

		tcpToWsBroadcast.Submit(msg)
	}

	// close wsToTcpChannel to terminate handleTCPRequestWrite
	wsToTcpBroadcast.Unregister(wsToTcpChannel)
	close(wsToTcpChannel)
}

func handleTCPRequestWrite(wg *sync.WaitGroup, wsToTcpChannel chan interface{}, mrw *messageReadWriter) {
	defer wg.Done()

	for msg := range wsToTcpChannel {
		m, ok := msg.([]byte)
		if !ok {
			log.Println("TCP: Invalid type for message")
			continue
		}

		_, err := mrw.WriteMessage(m)
		if err != nil {
			break
		}
	}
}

// ----------------------------------------------------------------- Web Server

var upgrader = websocket.Upgrader{}

func handleWs(w http.ResponseWriter, r *http.Request) {
	c, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("WS: upgrade:", err)
		return
	}

	log.Println("WS: Accepted new connection")
	defer c.Close()
	defer log.Println("WS: Closing connection")

	// cleanup will be done by handleWsRead
	tcpToWsChannel := make(chan interface{})
	tcpToWsBroadcast.Register(tcpToWsChannel)

	var wg sync.WaitGroup
	wg.Add(2)

	go handleWsWrite(&wg, tcpToWsChannel, c)
	go handleWsRead(&wg, tcpToWsChannel, c)

	wg.Wait()
}

func handleWsWrite(wg *sync.WaitGroup, tcpToWsChannel chan interface{}, conn *websocket.Conn) {
	defer wg.Done()

	for msg := range tcpToWsChannel {
		m, ok := msg.([]byte)
		if !ok {
			log.Println("WS: Invalid type for message")
			continue
		}

		if conn.WriteMessage(websocket.TextMessage, m) != nil {
			break
		}
	}
}

func handleWsRead(wg *sync.WaitGroup, tcpToWsChannel chan interface{}, conn *websocket.Conn) {
	defer wg.Done()

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			break
		}

		wsToTcpBroadcast.Submit(msg)
	}

	// close tcpToWsChannel to terminate handleWsWrite
	tcpToWsBroadcast.Unregister(tcpToWsChannel)
	close(tcpToWsChannel)
}

func listenAndServeHTTP(port int) {
	log.Println("Starting HTTP listener on", port)

	http.HandleFunc("/ws", handleWs)
	fs := http.FileServer(http.Dir("web"))
	http.Handle("/", fs)

	err := http.ListenAndServe(":"+strconv.Itoa(port), nil)
	if err != nil {
		log.Println("HTTP:", err)
	}
}

// ---------------------------------------------------------- Message Semantics

type messageReadWriter struct {
	rw io.ReadWriter
}

func newMessageReadWriter(rw io.ReadWriter) *messageReadWriter {
	return &messageReadWriter{rw}
}

func (m *messageReadWriter) ReadMessage() ([]byte, error) {
	var size uint64
	err := binary.Read(m.rw, binary.BigEndian, &size)
	if err != nil {
		return nil, err
	}

	p := make([]byte, size)
	_, err = io.ReadFull(m.rw, p)
	if err != nil {
		return nil, err
	}

	return p, nil
}

func (m *messageReadWriter) WriteMessage(p []byte) (int, error) {
	err := binary.Write(m.rw, binary.BigEndian, uint64(len(p)))
	if err != nil {
		return 0, err
	}

	return m.rw.Write(p)
}

// ---------------------------------------------------------- Message Generator

const (
	numNodes          = 16
	memLimit          = 100000
	maxTaskThroughput = 100
	networkLimit      = 100000
)

var timeStep int64 = 1

type statusUpdate struct {
	Time       int64              `json:"time"`
	Type       string             `json:"type"`
	Speed      float64            `json:"speed"`
	Efficiency float64            `json:"efficiency"`
	Power      float64            `json:"power"`
	Score      float64            `json:"score"`
	Nodes      []nodeStatusUpdate `json:"nodes"`
}

type nodeStatusUpdate struct {
	ID                    int64         `json:"id"`
	State                 string        `json:"state"`
	CPULoad               float64       `json:"cpu_load"`
	MemLoad               int64         `json:"mem_load"`
	TotalMemory           int64         `json:"total_memory"`
	TaskThroughput        int64         `json:"task_throughput"`
	WeightedTaskThrougput float64       `json:"weighted_task_througput"`
	NetworkIn             int64         `json:"network_in"`
	NetworkOut            int64         `json:"network_out"`
	Speed                 float64       `json:"speed"`
	Efficiency            float64       `json:"efficiency"`
	Power                 float64       `json:"power"`
	IdleRate              float64       `json:"idle_rate"`
	OwnedData             []interface{} `json:"owned_data"`
}

func randNodeStatusUpdate(id int64) nodeStatusUpdate {
	return nodeStatusUpdate{
		ID:                    id,
		State:                 "online",
		CPULoad:               rand.Float64(),
		MemLoad:               int64(rand.Intn(memLimit)),
		TotalMemory:           memLimit * 1.2,
		TaskThroughput:        int64(rand.Intn(maxTaskThroughput)),
		WeightedTaskThrougput: rand.Float64() * 10,
		NetworkIn:             int64(rand.Intn(networkLimit)),
		NetworkOut:            int64(rand.Intn(networkLimit)),
		Speed:                 rand.Float64(),
		Efficiency:            rand.Float64(),
		Power:                 rand.Float64(),
		IdleRate:              rand.Float64(),
		OwnedData:             make([]interface{}, 0),
	}
}

func messageGenerator(updateInterval time.Duration) {
	log.Println("Starting Random Message Generator")

	for {
		var nodes []nodeStatusUpdate
		for id := int64(0); id < numNodes; id++ {
			nodes = append(nodes, randNodeStatusUpdate(id))
		}

		if timeStep%20 > 10 {
			nodes[7].State = "offline"
			nodes[11].State = "offline"
		}

		msg := statusUpdate{
			Time:       timeStep,
			Type:       "status",
			Speed:      rand.Float64(),
			Efficiency: rand.Float64(),
			Power:      rand.Float64(),
			Score:      rand.Float64(),
			Nodes:      nodes,
		}

		data, err := json.Marshal(msg)
		if err != nil {
			log.Println("RMG: Could not marshal message:", err)
			break
		}

		tcpToWsBroadcast.Submit(data)

		timeStep++

		time.Sleep(updateInterval)
	}
}
