//  PAES_Settings.java 
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
import jmetal.metaheuristics.paes.PAES;
import jmetal.metaheuristics.paes.PAESAAFM;
import jmetal.operators.mutation.Mutation;
import jmetal.operators.mutation.MutationFactory;
import jmetal.problems.ProblemFactory;
import jmetal.util.JMException;

import java.util.HashMap;

import es.us.isa.fama.operations.AAFMProblem;

/**
 * Settings class of algorithm PAES
 */
public class PAESAAFM_Settings extends AAFMSettings{

  private int maxEvaluations_ ;
  private int archiveSize_    ;
  private int biSections_     ;
  private double mutationProbability_ ;
  private double distributionIndex_   ;

  /**
   * Constructor
   */
  public PAESAAFM_Settings(AAFMProblem problem) {
    super(problem) ;

    // Default experiments.settings
    maxEvaluations_ = 25000 ;
    archiveSize_    = 100   ;
    biSections_     = 5     ;
    mutationProbability_ = 1.0/problem_.getNumberOfVariables() ;
    distributionIndex_   = 20.0 ;
  } // PAES_Settings

  /**
   * Configure the MOCell algorithm with default parameter experiments.settings
   * @return an algorithm object
   * @throws jmetal.util.JMException
   */
  public Algorithm configure() throws JMException {
    Algorithm algorithm ;
    Mutation  mutation   ;

    HashMap  parameters ; // Operator parameters

    // Creating the problem
    algorithm = new PAESAAFM(problem_) ;

    // Algorithm parameters
    algorithm.setInputParameter("maxEvaluations", maxEvaluations_);
    algorithm.setInputParameter("biSections", biSections_);
    algorithm.setInputParameter("archiveSize",archiveSize_ );

    // Mutation (Real variables)
    parameters = new HashMap() ;
    parameters.put("probability", mutationProbability_) ;
    parameters.put("distributionIndex", distributionIndex_) ;
    mutation = MutationFactory.getMutationOperator("IntPolynomialMutation", parameters);                    

    // Add the operators to the algorithm
    algorithm.addOperator("mutation", mutation);

    return algorithm ;
  } // configure
} // PAES_Settings
