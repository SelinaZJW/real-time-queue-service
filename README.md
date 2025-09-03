## Real Time Queue Service

Inspired by a frustrating experience getting tickets to Tomorrowland, this service allows users to join the queue and subscribe to updates of their real-time positions, and allows workers or operators to fetch the next user from the queue, while notifying users still in the queue of position changes.

The service utilises fs2 and cats effect libraries and implements a streaming endpoint for the user to receive updates and a simple GET endpoint for the worker.

See [tech post](https://selinazjw.com/blog/real-time-queue-service-tech-post) discussing the codebase in detail.

### Architecture

<img alt="architecture" src="/docs/images/architecture.png" width={1000} height={500} content-align="center" />

### Demo

- Users enqueue
- Workers dequeue
- Users receive real-time position updates

[demo.webm](https://raw.githubusercontent.com/SelinaZJW/real-time-queue-service/main/docs/videos/demo.webm)
