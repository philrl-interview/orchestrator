# Orchestrator

Orchestrator is a simple job runner with an HTTP API. 

There are two basic concepts in Orchestrator: Tasks and Jobs.

## Tasks

Tasks are a names command to be run. Currently, only bash commands are supported.

## Jobs

Jobs are a set of tasks to be run in order, and can be scheduled to run on a given interval.

## Creating Tasks

Tasks can be created via the API.

    POST /tasks
    {
      "name": "test_task",
      "command": "date >> somefile.txt"
    }

## Running Tasks

A single task can be run on demand via the API

    POST /task/run/test_task
    
## Creating Jobs

A job can be created via the API

    POST /jobs
    {
      "name": "test_job",
      "tasks": ["test_task4"],
      "frequency": {
          "seconds": 5
      }
     }
 
The `frequency` field can take `seconds`, `minutes`, `hours`, or `days` with a number, or use `"once": true` to create the job and run it once.

## Running Jobs

Jobs are run on a schedule as provided when they are created, but can also be run on demand via the api

    POST /jobs/run/test_job
    
## Deleting Jobs

Jobs can be deleted through the API

    DELETE /jobs/test_job
    
## Viewing Tasks and Jobs

You can see all tasks or jobs, or individual ones, through the API

    GET /tasks
    GET /tasks/test_task
    GET /jobs
    GET /jobs/test_job
    

  
