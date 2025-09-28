# Kafka Setup for RetailPulse (Apache Kafka KRaft Mode)

This directory contains the necessary Kubernetes YAML files to deploy a standalone Apache Kafka broker (using KRaft mode, no Zookeeper) and a Kafka Web UI (Provectus Labs Kafka UI) in the `ns-retailpulse` namespace.

## Prerequisites

- A running Kubernetes cluster.
- `kubectl` configured to interact with your cluster.
- The `ns-retailpulse` namespace exists (`kubectl create namespace ns-retailpulse` if needed).

## Deploying Apache Kafka (KRaft Mode)

1.  **Navigate to the Kafka Broker Directory:**
    ```bash
    cd kafka-setup/kafka-broker
    ```

2.  **Apply the Kubernetes Manifests:**
    ```bash
    kubectl apply -f .
    ```
    Or apply individually:
    ```bash
    kubectl apply -f 02-kafka-configmap.yaml -n ns-retailpulse
    kubectl apply -f 03-kafka-service.yaml -n ns-retailpulse
    kubectl apply -f 04-kafka-statefulset.yaml -n ns-retailpulse
    # Note: 01-kafka-pvc.yaml is not needed if using volumeClaimTemplates in StatefulSet
    ```

3.  **Verify Deployment:**
    ```bash
    kubectl get pods,svc,pvc -n ns-retailpulse -l app=kafka-retailpulse
    ```
    You should see:
    - A StatefulSet `kafka` with 1/1 ready replica.
    - A Pod `kafka-0` in the `Running` state.
    - A Service `kafka-service` of type `NodePort` with ports 9092, 9093, 9094.
    - A PVC `kafka-data-kafka-0` in the `Bound` state.

## Deploying Kafka Web UI

1.  **Navigate to the Kafka UI Directory:**
    ```bash
    cd ../kafka-ui # Relative path from kafka-broker
    ```

2.  **Review `01-kafka-ui-deployment.yaml` and `02-kafka-ui-service.yaml`:** Ensure the Kafka bootstrap server (`kafka-service.ns-retailpulse.svc.cluster.local:9092`) and NodePort (`30095`) are correct.

3.  **Apply the Kubernetes Manifests:**
    ```bash
    kubectl apply -f .
    ```
    Or apply individually:
    ```bash
    kubectl apply -f 01-kafka-ui-deployment.yaml -n ns-retailpulse
    kubectl apply -f 02-kafka-ui-service.yaml -n ns-retailpulse
    # kubectl apply -f 03-kafka-ui-configmap.yaml -n ns-retailpulse # If using advanced config
    ```

4.  **Verify Deployment:**
    ```bash
    kubectl get pods,svc -n ns-retailpulse -l app=kafka-ui-retailpulse
    ```
    You should see the `kafka-ui` pod running and the `kafka-ui-service` service.

## Accessing Services

### Kafka Broker

- **Internal Access (from within the cluster):**
  - Service Name: `kafka-service.ns-retailpulse.svc.cluster.local`
  - Ports:
    - `9092`: Internal PLAINTEXT listener.
    - `9093`: Controller listener.
    - `9094`: External listener (mapped internally).
- **External Access (from outside the cluster, e.g., local machine or UI):**
  - NodePort: `30094`.
  - Connect using: `<ANY_CLUSTER_NODE_IP>:30094`.
    - If using `minikube`, get the IP: `minikube ip` then use `<MINIKUBE_IP>:30094`.
    - If using `kind`, use `localhost:30094`.
    - If using Docker Desktop Kubernetes, use `localhost:30094`.
    - If using a cloud provider, find any worker node's external IP.

### Kafka Web UI

- **Access URL:**
  - NodePort: `30095`.
  - Open your browser and go to: `http://<ANY_CLUSTER_NODE_IP>:30095`.
    - `http://localhost:30095` (Minikube, Kind, Docker Desktop).
    - `http://<EXTERNAL_NODE_IP>:30095` (Cloud clusters).

## Troubleshooting

- **Check Pod Logs:**
  ```bash
  # Kafka Broker
  kubectl logs -f kafka-0 -n ns-retailpulse
  # Kafka UI
  kubectl logs -f <kafka-ui-pod-name> -n ns-retailpulse