package main

import (
	"fmt"
	"log"
	"os"
	"strconv"
	"time"

	"github.com/confluentinc/confluent-kafka-go/kafka"
)

func main() {
	if len(os.Args) != 2 {
		log.Fatal("Expecting single topic argument")
	} else if err := run(os.Args[1]); err != nil {
		log.Fatal(err)
	}
}

func run(topic string) error {
	// Create consumer
	c, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers": "localhost:9092",
		// Just use a random group ID to start from beginning
		"group.id":          strconv.FormatInt(time.Now().Unix(), 10),
		"auto.offset.reset": "earliest",
		"isolation.level":   "read_committed",
	})
	if err != nil {
		return fmt.Errorf("failed opening consumer: %w", err)
	}
	defer c.Close()
	// Subscribe
	if err := c.SubscribeTopics([]string{topic}, nil); err != nil {
		return fmt.Errorf("failed subscribing; %w", err)
	}
	// Read until timeout
	for {
		msg, err := c.ReadMessage(5 * time.Second)
		if kafkaErr, ok := err.(kafka.Error); ok && kafkaErr.Code() == kafka.ErrTimedOut {
			fmt.Println("Done")
			return nil
		} else if err != nil {
			return fmt.Errorf("failed reading: %w", err)
		}
		fmt.Println(string(msg.Value))
	}
}
