//  OMOPSO_Settings.java 
//
//  Authors:
//       Antonio J. Nebro <antonio@lcc.uma.es>
//       Juan J. Durillo <durillo@lcc.uma.es>
//
//  Copyright (c) 2011 Antonio J. Nebro, Juan J. Durillo
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
// 
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.

package es.us.isa.jmetal.experimentation.settings;

import jmetal.core.Algorithm;
import jmetal.experiments.Settings;
import jmetal.metaheuristics.omopso.OMOPSO;
import jmetal.metaheuristics.omopso.OMOPSOAAFM;
import jmetal.operators.mutation.Mutation;
import jmetal.operators.mutation.NonUniformMutation;
import jmetal.operators.mutation.UniformMutation;
import jmetal.operators.mutation.integer.IntNonUniformMutation;
import jmetal.operators.mutation.integer.IntUniformMutation;
import jmetal.problems.ProblemFactory;
import jmetal.util.JMException;

import java.util.HashMap;

import es.us.isa.fama.operations.AAFMProblem;

/**
 * Settings class of algorithm OMOPSO
 */
public class OMOPSOAAFM_Settings extends AAFMSettings{
  
  private int    swarmSize_         ;
  private int    maxIterations_     ;
  private int    archiveSize_       ;
  private double perturbationIndex_ ;
  private double mutationProbability_ ;
  
  /**
   * Constructor
   */
  public OMOPSOAAFM_Settings(AAFMProblem problem) {
    super(problem) ;   
    // Default experiments.settings
    swarmSize_         = 100 ;
    maxIterations_     = 250 ;
    archiveSize_       = 100 ;
    perturbationIndex_ = 0.5 ;
    mutationProbability_ = 1.0/problem_.getNumberOfVariables() ;
  } // OMOPSO_Settings
  
  /**
   * Configure OMOPSO with user-defined parameter experiments.settings
   * @return A OMOPSO algorithm object
   * @throws jmetal.util.JMException
   */
  public Algorithm configure() throws JMException {
    Algorithm algorithm ;
    Mutation  uniformMutation ;
    Mutation nonUniformMutation ;

    HashMap  parameters ; // Operator parameters

    // Creating the problem
    algorithm = new OMOPSOAAFM(problem_) ;

    // Algorithm parameters
    algorithm.setInputParameter("swarmSize",swarmSize_);
    algorithm.setInputParameter("archiveSize",archiveSize_);
    algorithm.setInputParameter("maxIterations",maxIterations_);
    
    
    parameters = new HashMap() ;
    parameters.put("probability", mutationProbability_) ;
    parameters.put("perturbation", perturbationIndex_) ;
    uniformMutation = new IntUniformMutation(parameters);
    
    parameters = new HashMap() ;
    parameters.put("probability", mutationProbability_) ;
    parameters.put("perturbation", perturbationIndex_) ;
    parameters.put("maxIterations", maxIterations_) ;
    nonUniformMutation = new IntNonUniformMutation(parameters);

    // Add the operators to the algorithm
    algorithm.addOperator("uniformMutation",uniformMutation);
    algorithm.addOperator("nonUniformMutation",nonUniformMutation);

    return algorithm ;
  } // configure
} // OMOPSO_Settings
